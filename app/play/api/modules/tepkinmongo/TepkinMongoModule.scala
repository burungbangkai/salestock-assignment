package play.api.modules.tepkinmongo

import javax.inject._

import com.github.jeroenr.tepkin.MongoClient
import play.api._
import play.api.inject.{ApplicationLifecycle, Binding, Module}

/**
  * MongoDB module.
  */
@Singleton
final class TepkinMongoModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(bind[TepkinMongoApi].to[DefaultTepkinMongoApi].in[Singleton])

}

trait TepkinMongoApi {
  def client: MongoClient
  def db: String
}

final class DefaultTepkinMongoApi @Inject()(configuration: Configuration,
                                             applicationLifecycle: ApplicationLifecycle) extends TepkinMongoApi {

  val client = MongoClient(configuration.getString("mongodb.uri").getOrElse(throw new IllegalStateException("Please configure mongodb.uri in your application.conf for example \"mongodb://localhost\" ")))
  val db = configuration.getString("mongodb.database").getOrElse(throw new IllegalStateException("Please configure mongodb.database in your application.conf for example \"example\""))
}