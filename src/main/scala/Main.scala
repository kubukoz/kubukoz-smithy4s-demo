import demo.User
import smithy4s.schema.Schematic
import doobie.util.Write
import doobie.implicits._
import smithy4s.schema.StubSchematic
import demo.UserId
import demo.Username
import smithy4s.Timestamp
import smithy4s.schema.Field
import smithy4s.Hints
import smithy4s.Refinement
import java.time.Instant
import cats.effect.IO
import doobie.postgres.implicits._
import doobie.util.transactor
import doobie.util.Put
import cats.implicits.given
import cats.effect.IOApp
import doobie.util.Read
import doobie.util.Get
import smithy4s.schema.SchemaVisitor
import smithy4s.ShapeId
import smithy4s.schema.Schema
import smithy4s.schema.EnumValue
import smithy4s.Lazy
import smithy4s.schema.Primitive
import smithy4s.schema.Alt
import smithy4s.schema.Alt.WithValue
import java.util.UUID
object Main extends IOApp.Simple {

  implicit val writeUser: Write[User] = User.schema.compile(ToWriteVisitor)

  implicit val readUser: Read[User] = User.schema.compile(ToReadVisitor)

  val user = User(
    UserId(UUID.randomUUID().toString),
    Username("u"),
    age = Some(42),
    lastUpdatedAt = Some(Timestamp.nowUTC())
  )

  val xa = transactor.Transactor.fromDriverManager[IO](
    "org.postgresql.Driver", // driver classname
    "jdbc:postgresql:postgres", // connect URL (driver-specific)
    "postgres", // user
    "p" // password
  )

  def run = {
    sql"""create table if not exists
       users(id text primary key, name text not null, age int, lastUpdatedAt timestamp)""".update.run *>
      sql"""insert into users values($user)""".update.run *>
      sql"""select * from users""".query[User].nel
  }.transact(xa).flatMap(_.traverse_(IO.println(_)))
}

object ToPutVisitor extends smithy4s.PolyFunction[Schema, Put] {
  def apply[A](fa: Schema[A]): Put[A] = fa match {
    case Schema.PrimitiveSchema(shapeId, hints, tag) =>
      tag match {
        case Primitive.PInt    => Put[Int]
        case Primitive.PString => Put[String]
        case Primitive.PTimestamp =>
          Put[Instant].contramap[Timestamp](_.toInstant)
      }

    case Schema.BijectionSchema(schema, f, g) =>
      schema.compile(this).contramap(g)
  }
}

object ToWriteVisitor extends smithy4s.PolyFunction[Schema, Write] {
  def apply[S](fa: Schema[S]): Write[S] = fa match {
    case Schema.StructSchema(shapeId, hints, fields, make) => {
      val writes = fields.map { case (f: Field[Schema, S, a]) =>
        f.mapK(ToPutVisitor)
          .foldK(new Field.FolderK[Put, S, Write] {
            def onOptional[A](
                label: String,
                instance: Put[A],
                get: S => Option[A]
            ): Write[Option[A]] = Write.fromPutOption(instance)

            def onRequired[A](
                label: String,
                instance: Put[A],
                get: S => A
            ): Write[A] = Write.fromPut(instance)
          })
          .contramap(f.get)

      }

      writes.foldLeft(Write.unitComposite.contramap[S](_ => ()))(
        (_, _).contramapN(b => (b, b))
      )
    }
  }
}

object ToGetVisitor extends smithy4s.PolyFunction[Schema, Get] {
  def apply[A](fa: Schema[A]): Get[A] = fa match {
    case Schema.PrimitiveSchema(shapeId, hints, tag) =>
      tag match {
        case Primitive.PInt    => Get[Int]
        case Primitive.PString => Get[String]
        case Primitive.PTimestamp =>
          Get[Instant].map(Timestamp.fromInstant(_))
      }

    case Schema.BijectionSchema(schema, f, g) =>
      schema.compile(this).map(f)
  }
}

object ToReadVisitor extends smithy4s.PolyFunction[Schema, Read] {
  def apply[S](fa: Schema[S]): Read[S] = fa match {
    case Schema.StructSchema(shapeId, hints, fields, make) => {
      val reads = fields.map { case (f: Field[Schema, S, a]) =>
        f.mapK(ToGetVisitor)
          .foldK(new Field.FolderK[Get, S, Read] {
            def onOptional[A](
                label: String,
                instance: Get[A],
                get: S => Option[A]
            ): Read[Option[A]] = Read.fromGetOption(instance)

            def onRequired[A](
                label: String,
                instance: Get[A],
                get: S => A
            ): Read[A] = Read.fromGet(instance)
          })

      }

      reads
        .traverse(a => a.widen[Any])
        .map(make)
    }
  }
}
