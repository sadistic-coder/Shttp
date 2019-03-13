import java.io.File

import Main.helloWorldService
import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import org.http4s.{Header, HttpRoutes, Request, StaticFile, Status}
import org.http4s.syntax._
import org.http4s.dsl.io._
import org.http4s.server.blaze._
import java.util.concurrent._

import scala.concurrent.ExecutionContext

object Main extends IOApp {

  val blockingEc = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  var count: Int = 0

  val helloWorldService = HttpRoutes.of[IO] {
    case request@GET -> Root =>
      StaticFile.fromFile(new File("resource/main.html"), blockingEc, Some(request))
        .getOrElseF(NotFound())
    case request@GET -> Root / "heart" =>
      Ok(count.toString())
    case request@GET -> Root / "love" => {
      count += 1
      Ok()
    }
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(helloWorldService)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
