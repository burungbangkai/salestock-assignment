import com.github.jeroenr.bson.element.BsonObjectId
import models._
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.ws.WSClient
import play.api.test.Helpers._
import play.api.libs.json._

/**
  * Created by bistokdl on 3/30/16.
  */
class CartSpec extends PlaySpec with OneServerPerSuite {
  val productName = "dodol garut"
  val couponCode = "TEST"
  val productRepo = app.injector.instanceOf[ProductRepo]
  val couponRepo = app.injector.instanceOf[CouponRepo]
  val cartRepo = app.injector.instanceOf[CartRepo]


  "test product repo" should {
    await(productRepo.drop)
    "create one product" in {
      val price = 1200.0
      val result = await(productRepo.create(productName, price))
      result.isDefined mustBe true
      val newlyCreated = await(productRepo.findByName(productName)).get
      newlyCreated mustNot equal(null)
      newlyCreated.name must fullyMatch regex productName
      newlyCreated.price must equal(price)
    }
    "successfully edit a product" in {
      val newName = "DODOL"
      val newPrice = 1250.0
      val result = await(productRepo.create("OLD",1100.0)).get
      val updatedProduct = result.update(newName,newPrice)
      val updatedResult = await(productRepo.update(updatedProduct))
      updatedResult must not equal(None)
      updatedResult.get._id must equal(result._id)
      updatedResult.get.price must not equal(result.price)
      updatedResult.get.price must equal(newPrice)
      updatedResult.get.name must equal(newName)
    }
    "successfully delete a product" in {
      val product = await(productRepo.findByName("DODOL")).get
      val result = await(productRepo.delete(product._id))
      result.ok mustBe true
      result.n must equal(1)
    }
    "not return deleted product" in{
      val product = await(productRepo.findByName("DODOL"))
      product must equal(None)
    }
  }

  "test coupon repo " should {
    await(couponRepo.drop)
    "create one coupon" in {
      val amount = 100.0
      val validity = true
      val result = await(couponRepo.create(couponCode, validity, amount)).get
      result mustNot equal(null)
      result.code must fullyMatch regex couponCode
      result.value must equal(amount)
      result.validity mustBe true
    }
    "successfully edit a coupon" in {
      val newCode = "DODOL"
      val newValidity = false;
      val newAmount = 200.0
      val result = await(couponRepo.findByCode(couponCode)).get
      val updatedCoupon = result.update(newCode,newValidity,newAmount)
      val updatedResult = await(couponRepo.update(updatedCoupon))
      updatedResult must not equal(None)
      updatedResult.get._id must equal(result._id)
      updatedResult.get.code must not equal(result.code)
      updatedResult.get.code must equal(newCode)
      updatedResult.get.validity must equal(newValidity)
      updatedResult.get.value must equal(newAmount)
    }
    "successfully delete coupon" in {
      val amount = 100.0
      val validity = true
      val dontCare = await(couponRepo.create(couponCode+"1", validity, amount)).get
      val result = await(couponRepo.delete(dontCare._id))
      result.ok mustBe true
      result.n must equal(1)
    }
    "not return deleted coupon" in{
      val amount = 100.0
      val validity = true
      val dontCare = await(couponRepo.create(couponCode+"2", validity, amount)).get
      val dontCareResult = await(couponRepo.delete(dontCare._id))
      val result = await(couponRepo.findby("_id",dontCare._id))
      result must equal(None)
    }
  }

  "test cart repo " should {
    await(cartRepo.drop)
    val customerId = "test"
    "create one cart" in {
      val result = await(cartRepo.create(customerId, List(), 0.0)).get
      result mustNot equal(null)
      result.coupon must equal(None)
      result.total must equal(0.0)
      result.sales must equal(List())
      val couponValue = result.coupon.map(coupon => coupon.value).getOrElse(0.0)
      couponValue must equal(0.0)
    }
    "successfully add an item to cart" in {
      val result = await(cartRepo.findByCustomerId(customerId)).get
      val item = await(productRepo.findByName(productName)).get
      val updatedCart = result.addItem(item)
      val updatedResult = await(cartRepo.update(updatedCart)).get
      updatedResult._id must equal(result._id)
      updatedResult.sales must have size 1
      updatedResult.sales.contains(item) mustBe true
      updatedResult.customer_id must equal(result.customer_id)
      updatedCart.total mustNot equal(result.total)
      updatedCart.total must equal(item.price)
      updatedCart.coupon must equal(None)
    }
    "successfully remove an item from a cart" in {
      val result = await(cartRepo.findByCustomerId(customerId)).get
      val item = await(productRepo.findByName(productName)).get
      val updatedCart = result.removeItem(item._id)
      val updatedResult = await(cartRepo.update(updatedCart)).get
      updatedResult._id must equal(result._id)
      updatedResult.sales must have size result.sales.size-1
      updatedResult.sales.contains(item) mustBe false
      updatedResult.customer_id must equal(result.customer_id)
      updatedResult.total mustNot equal(result.total)
      updatedResult.total must equal(result.total-item.price)
    }
    "successfully put a coupon" in {
      val result = await(cartRepo.findByCustomerId(customerId)).get
      val amount = 100.0
      val validity = true
      val coupon = await(couponRepo.create(couponCode,validity,amount)).get
      val updatedCart = result.setCoupon(coupon)
      val updatedResult = await(cartRepo.update(updatedCart)).get
      updatedResult must not equal(null)
      updatedResult.coupon must not equal(None)
      updatedResult.coupon must equal(updatedCart.coupon)
      updatedResult.total must equal(updatedCart.total)
      updatedResult.total must equal(0)
    }
    "successfully change total with coupon" in{
      val result = await(cartRepo.findByCustomerId(customerId)).get
      val coupon = await(couponRepo.findByCode(couponCode)).get
      val updatedCart = result.setCoupon(coupon)
      val item = await(productRepo.findByName(productName)).get
      val updatedCartWithItem = updatedCart.addItem(item)
      val updatedResult = await(cartRepo.update(updatedCartWithItem)).get
      updatedResult must not equal(null)
      updatedResult.coupon must not equal(None)
      updatedResult.coupon must equal(updatedCartWithItem.coupon)
      updatedResult.total must equal(updatedCartWithItem.total)
      updatedResult.total must not equal(0)
      updatedResult.total must equal(item.price-coupon.value)
    }
    "successfully delete a cart" in {
      val result = await(cartRepo.delete(customerId))
      result.ok must be equals true
      result.n must be equals(1)
    }
    "not return deleted cart" in {
      val result = await(cartRepo.findByCustomerId(customerId))
      result must equal(None)
    }
  }

  "cart controller logic" should {
    val wsClient = app.injector.instanceOf[WSClient]
    val serverUrl = s"http://localhost:$port"
    val customerId = BsonObjectId.generate.identifier
    "create a cart for given customerId" in {
      val response = await(wsClient.url(s"$serverUrl/$customerId/cart").put("none"))
      response mustNot equal(null)
      response.status mustBe OK
      response.json mustNot equal(null)
      val cart = Json.fromJson[Cart](response.json)
      cart.isSuccess mustBe true
      cart.get.customer_id must ===(customerId)
      cart.get.sales must have size 0
      cart.get.coupon must equal(None)
      cart.get.total must equal(0.0)
    }
    "get a cart for given customerId" in{
      val response = await(wsClient.url(s"$serverUrl/$customerId/cart").get())
      response mustNot equal(null)
      response.status mustBe OK
      response.json mustNot equal(null)
      val cart = Json.fromJson[Cart](response.json)
      cart.isSuccess mustBe true
      cart.get.customer_id must ===(customerId)
      cart.get.sales must have size 0
      cart.get.coupon must equal(None)
      cart.get.total must equal(0.0)
    }
    "add an item to customer's cart" in {
      val item = await(productRepo.findByName(productName)).get
      val response = await(wsClient.url(s"$serverUrl/$customerId/cart/item")
        .put(Json.toJson(item)))
      response mustNot equal(null)
      response.status mustBe OK
      response.json mustNot equal(null)
      val cart = Json.fromJson[Cart](response.json)
      cart.isSuccess mustBe true
      cart.get.customer_id must ===(customerId)
      cart.get.sales must have size 1
      cart.get.sales.contains(item) mustBe true
      cart.get.coupon must equal(None)
      cart.get.total must equal(item.price)
    }
    "reject adding item to non existing cart" in{
      val item = await(productRepo.findByName(productName)).get
      val response = await(wsClient.url(s"$serverUrl/NOT_EXIST_ID/cart/item")
        .put(Json.toJson(item)))
      response.status mustBe NOT_FOUND
    }
    "remove an item to customer's cart" in {
      val item = await(productRepo.findByName(productName)).get
      val response = await(wsClient.url(s"$serverUrl/$customerId/cart/item/${item._id}")
        .delete())
      response mustNot equal(null)
      response.status mustBe OK
      response.json mustNot equal(null)
      val cart = Json.fromJson[Cart](response.json)
      cart.isSuccess mustBe true
      cart.get.customer_id must ===(customerId)
      cart.get.sales must have size 0
      cart.get.sales.contains(item) mustBe false
      cart.get.coupon must equal(None)
      cart.get.total mustNot equal(item.price)
      cart.get.total must equal(0.0)
    }
    "reject removing item from non existing cart" in {
      val item = await(productRepo.findByName(productName)).get
      val response = await(wsClient.url(s"$serverUrl/NOT_EXIST_ID/cart/item/${item._id}").delete())
      response.status mustBe NOT_FOUND
    }
    "successfully add coupon to cart" in {
      val coupon = await(couponRepo.create("VALID",true, 200.00)).get
      val response = await(wsClient.url(s"$serverUrl/$customerId/cart/coupon/${coupon.code}").put("none"))
      response must not equal null
      response.status mustBe OK
      response.json mustNot equal(null)
      val cart = Json.fromJson[Cart](response.json)
      cart.isSuccess mustBe true
      cart.get.total must equal(0)
      cart.get.coupon must not equal (None)
      cart.get.coupon.get.code must equal(coupon.code)
      cart.get.coupon.get._id  must equal(coupon._id)
      cart.get.coupon.get.validity must equal(coupon.validity)
    }
    "successfully update total with coupon" in {
      val item = await(productRepo.findByName(productName)).get
      val response = await(wsClient.url(s"$serverUrl/$customerId/cart/item")
        .put(Json.toJson(item)))
      response mustNot equal(null)
      response.status mustBe OK
      response.json mustNot equal(null)
      val cart = Json.fromJson[Cart](response.json)
      cart.isSuccess mustBe true
      cart.get.total must equal(item.price-cart.get.coupon.get.value)
    }
    "reject invalid coupon code" in {
      val response = await(wsClient.url(s"$serverUrl/$customerId/cart/coupon/INVALID").put("none"))
      response.status mustBe BAD_REQUEST

    }
    "reject expired coupon code" in {
      val coupon = await(couponRepo.create("EXPIRED",false, 200.00)).get
      val response = await(wsClient.url(s"$serverUrl/$customerId/cart/coupon/${coupon.code}").put("none"))
      response.status mustBe BAD_REQUEST
    }
    "reject applying to non existing cart" in {
      val coupon = await(couponRepo.findByCode("VALID")).get
      val response = await(wsClient.url(s"$serverUrl/NOT_EXIST_ID/cart/coupon/${coupon.code}").put("none"))
      response.status mustBe NOT_FOUND
    }
    "successfully delete a cart" in {
      val response = await(wsClient.url(s"$serverUrl/$customerId/cart").delete())
      response.status mustBe OK
    }
    "not return deleted cart" in {

      val response = await(wsClient.url(s"$serverUrl/$customerId/cart").get())
      response.status mustBe NOT_FOUND
    }
  }
}
