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
import play.api.libs.json.{JsResult, JsSuccess, Json}
import play.api.modules.tepkinmongo.TepkinMongoApi

import scala.concurrent.Future
import scala.concurrent.duration._
/**
 * Created by bistokdl on 3/30/16.
 */
case class Coupon(_id: String, code: String, validity: Boolean, value: Double){
  def update(newCode: String, newValidity: Boolean, newAmount: Double): Coupon = {
    Coupon(_id, newCode,newValidity,newAmount)
  }
}

object Coupon {
  implicit val couponFormatter = Json.format[Coupon]
  def apply(bson: BsonDocument): JsResult[Coupon] = {
    Json.fromJson[Coupon](bson)
  }

  def toBsonDocument(coupon: Coupon): BsonDocument = {
    ("_id" := coupon._id) ~
      ("code" := coupon.code) ~
      ("validity":=coupon.validity)~
      ("value":=coupon.value)
  }
}

class CouponRepo @Inject()(tepkinMongoApi: TepkinMongoApi){
  implicit val ec = tepkinMongoApi.client.ec
  implicit val timeout: Timeout = 5.seconds

  val coupons = tepkinMongoApi.client(tepkinMongoApi.db)("coupons")

  def create(code: String, validity: Boolean, value: Double): Future[Option[Coupon]] = {
    val coupon =
      ("_id" := BsonObjectId.generate.identifier)~
        ("code" := code)~
        ("validity":= validity)~
        ("value":= value)
    coupons.insert(coupon).flatMap[Option[Coupon]](ir=>findByCode(code))
  }

  def all: Source[List[Coupon],ActorRef]={
    coupons.find(new BsonDocument()).map(l=>l.map(Coupon(_)).collect{
      case JsSuccess(c, _ ) => c
    })
  }

  def insert(cs: List[Coupon]) =
    coupons.insert(cs.map(Coupon.toBsonDocument))

  def findByCode(name: String): Future[Option[Coupon]] = {
    val byId = "code" := name
    coupons.findOne(byId).map(_.flatMap(Coupon(_).asOpt))
  }

  def delete(id:String): Future[DeleteResult]={
    val byId="_id" := id
    coupons.delete(byId)
  }

  def findby(key: String, value: String): Future[Option[Coupon]] = {
    val query = key := value
    coupons.findOne(query).map(_.flatMap(Coupon(_).asOpt))
  }

  def update(coupon: Coupon): Future[Option[Coupon]]={
    val byId = "_id" := coupon._id
    coupons.update(byId,Coupon.toBsonDocument(coupon)).flatMap(ir=>findby("_id",coupon._id))
  }

  def drop = coupons.drop()
}
