package models

import javax.inject.Inject

import akka.actor.ActorRef
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.github.jeroenr.bson.BsonDocument
import com.github.jeroenr.bson.BsonDsl._
import com.github.jeroenr.bson.element.BsonObjectId
import com.github.jeroenr.tepkin.protocol.result.DeleteResult
import helpers.BsonDocumentHelper._
import play.api.libs.json._
import play.api.modules.tepkinmongo.TepkinMongoApi

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by bistokdl on 3/30/16.
 */
case class Cart(_id:String, customer_id: String, sales: List[Product], coupon: Option[Coupon], total: Double)
object Cart {
  implicit val chartFormatter = Json.format[Cart]
  def apply(bson: BsonDocument): JsResult[Cart] ={
    Json.fromJson[Cart](bson)
  }

  def toBsonDocument(cart: Cart): BsonDocument = {
    ("_id" := cart._id)~
      ("customer_id" := cart.customer_id) ~
      ("sales" := cart.sales.map(Product.toBsonDocument))~
      ("coupon" := cart.coupon.map(Coupon.toBsonDocument))~
      ("total" := cart.total)
  }
  def addItem(cart: Cart, item: Product): Cart= {
    val sales = cart.sales :+ item
    val couponValue = cart.coupon.map(coupon=> coupon.value).getOrElse(0.0)
    Cart(cart._id, cart.customer_id,sales, cart.coupon,total(sales,couponValue))
  }
  def removeItem(cart: Cart, itemId: String): Cart = {
    val index = cart.sales.indexWhere(_._id==itemId)
    if(index<0)
      cart
    else{
      val sales = cart.sales.patch(index,Nil,1)
      val couponValue = cart.coupon.map(coupon=> coupon.value).getOrElse(0.0)
      Cart(cart._id,cart.customer_id,sales,cart.coupon,total(sales,couponValue))
    }
  }
  def total(sales: List[Product], couponCut: Double):Double ={
    val total = sales.map(item=>item.price).sum - couponCut
    if(total>0)
      total
    else
      0
  }
  def setCoupon(cart:Cart, coupon: Coupon): Cart ={
    val existingCouponValue = cart.coupon.map(coupon=> coupon.value).getOrElse(0.0)
    val cartCoupon = Option{coupon}
    Cart(cart._id,cart.customer_id,cart.sales,cartCoupon,
      total(cart.sales,coupon.value-existingCouponValue))
  }
}

class CartRepo @Inject()(tepkinMongoApi: TepkinMongoApi) {
  implicit val ec = tepkinMongoApi.client.ec
  implicit val timeout : Timeout = 5.seconds

  val carts = tepkinMongoApi.client(tepkinMongoApi.db)("carts")

  def delete(customerId: String): Future[DeleteResult]={
    val byId = ("customer_id":= customerId)
    carts.delete(byId)
  }

  def create(customerId: String): Future[Option[Cart]] = {
    create(customerId,List(),0.0)
  }
  def create(customerId: String, sales: List[Product], total: Double): Future[Option[Cart]] = {
    val id = BsonObjectId.generate.identifier
    val cart =
      ("_id" := id)~
        ("customer_id" := customerId)~
        ("sales" := sales)~
        ("coupon" := Option{None})~
        ("total":= total)
    carts.insert(cart).flatMap[Option[Cart]](ir=>
      findByCustomerId(customerId))
  }

  def all: Source[List[Cart],ActorRef]={
    carts.find(new BsonDocument()).map(l=>l.map(Cart(_)).collect{
      case JsSuccess(c, _ ) => c
    })
  }

  def update(cart: Cart): Future[Option[Cart]] = {
    val byId = "customer_id":= cart.customer_id
    carts.update(byId,Cart.toBsonDocument(cart))
      .flatMap(ir=>
        findBy("customer_id",cart.customer_id))
  }

  def insert(cs: List[Cart]) =
    carts.insert(cs.map(Cart.toBsonDocument))

  def findByCustomerId(customerId: String): Future[Option[Cart]] = {
    val byId = "customer_id" := customerId
    carts.findOne(byId).map(_.flatMap(Cart(_).asOpt))
  }

  def findBy(key: String, obj: Any): Future[Option[Cart]]={
    val byQuery = key := obj;
    carts.findOne(byQuery).map(_.flatMap(Cart(_).asOpt))
  }

  def drop = carts.drop()
}
