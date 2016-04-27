package controllers

import javax.inject.Inject

import action.AccessLogging
import akka.util.ByteString
import models._
import play.api.Logger
import play.api.http.HttpEntity.Streamed
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import play.mvc.Http.MimeTypes

import scala.concurrent.Future

/**
  * Created by bistokdl on 3/30/16.
  */
class ShoppingCart @Inject()(cartRepo: CartRepo, couponRepo: CouponRepo)
  extends Controller with AccessLogging{
  val logger = Logger(this.getClass())

  /**
    * an endpoint for creating a cart for a customer. it will only crate new cart
    * if there is no cart exist for given @customerId, else it will return the existing cart.
    * @param customerId
    * @return cart for given @customerId
    */
  def create(customerId: String) = Action.async {
    logger.debug(s"creating cart for $customerId")
    cartRepo.findByCustomerId(customerId).flatMap(optCart=>{
      val futureResult = if(optCart.isDefined)
                Future{optCart}
              else
                cartRepo.create(customerId)
      futureResult.map(c=>Ok(Json.toJson(c.get)))
    })
  }
  
  /**
    * an endpoint for retrieving all existing carts.
    * @return
    */
  def getAll = Action {
    logger.debug("requesting all existing cart")
    val carts = cartRepo.all
      .map(cart => Json.toJson[List[Cart]](cart))
      .map(js => ByteString(js.toString()))
    Ok.sendEntity(Streamed(carts, None, Some(MimeTypes.JSON)))
  }

  /**
    * endpoint for retrieving a cart belonging to a specific customer id
    * @param customerId
    * @return the cart object if exist, or 404 error if not.
    */
  def get(customerId: String) = Action.async {
    logger.debug(s"requesting cart for $customerId")
    cartRepo.findByCustomerId(customerId).map(maybeCart =>
      maybeCart
        .map(cart => Ok(Json.toJson(cart)))
        .getOrElse(NotFound(s"Undefined cart for customer id: $customerId"))
    )
  }

  /**
    * endpoint for deleting a cart
    * @param customerId
    * @return
    */
  def delete(customerId: String) = Action.async {
    logger.debug(s"deleting cart for $customerId")
    cartRepo.delete(customerId).map(result =>
      if (result.ok)
        Ok(s"cart for customer id: $customerId is deleted")
      else
        InternalServerError(s"Something wrong happened when deleting cart for $customerId. " +
          s"Write error: ${result.writeConcernError}")
    )
  }

  /**
    * endpoint for applying a coupon to a specific cart based on @customerId and coupon code
    * @param customerId
    * @param couponCode
    * @return 404 if @customerId doesn't have a cart;
    *         400 if the @couponCod is already invalid or not exist;
    *         updated cart if the cart exist and the coupon is valid
    */
  def applyCoupon(customerId: String, couponCode: String) = Action.async {
    logger.debug(s"applying coupon code: $couponCode for customer's ($customerId) cart")
    couponRepo.findByCode(couponCode)
      .withFilter(optCoupon => optCoupon.isDefined && optCoupon.get.validity).recover({case e: NoSuchElementException => Option[Coupon]{null}})
      .flatMap(maybeCoupon =>
        maybeCoupon.map(coupon => {
          cartRepo.findByCustomerId(customerId).flatMap(maybeCart =>
            maybeCart.map(cart => {
              val updatedCart = cart.setCoupon(coupon)
              cartRepo.update(updatedCart).map(result => Ok(Json.toJson(result.get)))
            }).getOrElse(
              Future {
                NotFound(s"Undefined cart for $customerId. Please create one first")
              }
            ))
        }).getOrElse(
          Future {
            BadRequest(s"invalid coupon: $couponCode")
          }
        ))
  }

  /**
    * end point for adding an item to a cart. it will only update a cart if
    * a cart is exist for given @customerId. Otherwise, it will return 404.
    *
    * the updated cart will contain the item in its item list (named sales)
    * and with updated total.
    *
    * the item to be added must be available inside the request body
    * @param customerId
    * @return updated cart or 404
    */
  def addItem(customerId: String) = Action.async(parse.json) { request =>
    val item = Json.fromJson[Product](request.body)
    logger.debug(s"adding item $item to customer's ($customerId) cart")
    cartRepo.findByCustomerId(customerId).flatMap(cart =>
      cart.map(custCart => {
        val updatedCart = custCart.addItem(item.get)
        cartRepo.update(updatedCart).map(result =>
          Ok(Json.toJson(result.get)))
      }
      ).getOrElse(
        Future {
          NotFound(s"Undefined cart for $customerId. Please create one first")
        }
      )
    )
  }

  /**
    * end point for removing an item to a cart. it will only update a cart if
    * a cart is exist for given @customerId. Otherwise, it will return 404
    * @param customerId
    * @return updated cart (the item list and total) or 404
    */
  def removeItem(customerId: String, itemId: String) = Action.async {
    logger.debug(s"removing item with id: $itemId from customer's ($customerId) cart")
    cartRepo.findByCustomerId(customerId).flatMap(cart =>
      cart.map(custCart => {
        val updatedCart = custCart.removeItem(itemId)
        cartRepo.update(updatedCart).map(result =>
          Ok(Json.toJson(result.get)))
      }).getOrElse(
        Future {
          NotFound(s"Undefined cart for $customerId. Please create one first")
        }
      )
    )
  }
}
