package action

/**
  * Created by bistokdl on 3/31/16.
  */
import scala.concurrent.Future
import play.api.Logger
import play.api.mvc._

trait AccessLogging {

  val accessLogger = Logger("access")

  object AccessLoggingAction extends ActionBuilder[Request] {

    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      accessLogger.info(s"method=${request.method} uri=${request.uri} remote-address=${request.remoteAddress}")
      block(request)
    }
  }
}
