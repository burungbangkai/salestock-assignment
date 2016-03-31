package action

import play.api.mvc._

import scala.concurrent.Future

/**
  * Created by bistokdl on 3/30/16.
  */
class UserRequest[A] (val userName: Option[String], request: Request[A]) extends WrappedRequest[A](request)

object UserAction extends
  ActionBuilder[UserRequest] with ActionTransformer[Request, UserRequest] {
    def transform[A](request: Request[A]) = Future.successful {
      new UserRequest(request.headers.get("username"),request)
    }
}

