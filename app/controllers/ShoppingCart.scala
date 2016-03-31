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
    * @return
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

//    cartRepo.create(customerId, List(), 0)
//      .map(newCart => Ok(Json.toJson(newCart.get)))
  }

  /**
    * an endpoint for geting all the existing cart.
    * @return
    */
  def getAll = Action {
    logger.debug("requesting all existing cart")
    val carts = cartRepo.all
      .map(cart => Json.toJson[List[Cart]](cart))
      .map(js => ByteString(js.toString()))
    Ok.sendEntity(Streamed(carts, None, Some(MimeTypes.JSON)))
  }

  def get(customerId: String) = Action.async {
    logger.debug(s"requesting cart for $customerId")
    cartRepo.findByCustomerId(customerId).map(maybeCart =>
      maybeCart
        .map(cart => Ok(Json.toJson(cart)))
        .getOrElse(NotFound(s"Undefined cart for customer id: $customerId"))
    )
  }

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

  def applyCoupon(customerId: String, couponCode: String) = Action.async {
    logger.debug(s"applying coupon code: $couponCode for customer's ($customerId) cart")
    couponRepo.findByCode(couponCode)
      .withFilter(optCoupon => optCoupon.isDefined && optCoupon.get.validity).recover({case e: NoSuchElementException => Option[Coupon]{null}})
      .flatMap(maybeCoupon =>
        maybeCoupon.map(coupon => {
          cartRepo.findByCustomerId(customerId).flatMap(maybeCart =>
            maybeCart.map(cart => {
              val updatedCart = Cart.setCoupon(cart, coupon)
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
    //    couponRepo.findByCode(couponCode).flatMap(maybeCoupon=>
    //      cartRepo.findByCustomerId(customerId).flatMap(cart=>{
    //        val updatedCart = Cart.setCoupon(cart.get,maybeCoupon.get)
    //        cartRepo.update(updatedCart).map(result=> Ok(Json.toJson(result)))
    //      })
    //    )
  }

  def addItem(customerId: String) = Action.async(parse.json) { request =>
    val item = Json.fromJson[Product](request.body)
    logger.debug(s"adding item $item to customer's ($customerId) cart")
    cartRepo.findByCustomerId(customerId).flatMap(cart =>
      cart.map(custCart => {
        val updatedCart = Cart.addItem(custCart, item.get)
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

  def removeItem(customerId: String, itemId: String) = Action.async {
    logger.debug(s"removing item with id: $itemId from customer's ($customerId) cart")
    cartRepo.findByCustomerId(customerId).flatMap(cart =>
      cart.map(custCart => {
        val updatedCart = Cart.removeItem(custCart, itemId)
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
