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
case class Product(_id: String, name: String, price: Double)

object Product {
  implicit val productFormatter = Json.format[Product]
  def apply(bson: BsonDocument): JsResult[Product] = {
    Json.fromJson[Product](bson)
  }

  def toBsonDocument(product: Product): BsonDocument = {
    ("_id" := product._id)~
      ("name" := product.name)~
      ("price":= product.price)
  }
  def update(product: Product, newName: String, newPrice: Double): Product = {
    Product(product._id,newName,newPrice)
  }
}

class ProductRepo @Inject()(tepkinMongoApi: TepkinMongoApi) {
  implicit val ec = tepkinMongoApi.client.ec
  implicit val timeout: Timeout = 5.seconds

  val products = tepkinMongoApi.client(tepkinMongoApi.db)("product")

  def create(name: String, price: Double): Future[Option[Product]] = {
    val id = BsonObjectId.generate.identifier
    val product =
      ("_id" := id)~
        ("name" := name) ~
        ("price" := price)
    products.insert(product).flatMap[Option[Product]](ir=>findBy("_id",id))
  }

  def all: Source[List[Product],ActorRef] = {
    products.find(new BsonDocument()).map(l => l.map(Product(_)).collect {
      case JsSuccess(p, _ ) => p
    })
  }

  def insert(ps: List[Product]) =
    products.insert(ps.map(Product.toBsonDocument))

  def findByName(name: String): Future[Option[Product]]={
    val byId = "name" := name
    products.findOne(byId).map(_.flatMap(Product(_).asOpt))
  }

  def findBy(key: String, value: String): Future[Option[Product]]={
    val query = key := value
    products.findOne(query).map(_.flatMap(Product(_).asOpt))
  }

  def update(product: Product): Future[Option[Product]] = {
    val byId = "_id" := product._id
    products.update(byId,Product.toBsonDocument(product)).flatMap(ir=>findBy("_id",product._id))
  }

  def delete(id: String): Future[DeleteResult] = {
    val byId = "_id" := id
    products.delete(byId)
  }

  def drop = products.drop()
}
