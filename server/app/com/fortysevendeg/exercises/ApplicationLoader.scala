/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package com.fortysevendeg.exercises

import com.fortysevendeg.exercises.controllers._
import com.fortysevendeg.exercises.utils._
import com.typesafe.config.ConfigFactory
import doobie.contrib.hikari.hikaritransactor.HikariTransactor
import doobie.util.transactor.{ DataSourceTransactor, Transactor }
import play.api.ApplicationLoader.Context
import play.api._
import play.api.db.{ DBComponents, HikariCPComponents }
import play.api.libs.ws._
import play.api.libs.ws.ning.NingWSClient
import router.Routes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.concurrent.Task
import scalaz.{ -\/, \/- }

import play.api.db.evolutions.{ DynamicEvolutions, EvolutionsComponents }

class ExercisesApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {
    val mode = context.environment.mode.toString.toLowerCase
    new Components(context.copy(
      initialConfiguration = context.initialConfiguration
      ++ Configuration(ConfigFactory.load(s"application.$mode.conf"))
    )).application
  }
}

class Components(context: Context)
    extends BuiltInComponentsFromContext(context)
    with DBComponents
    with EvolutionsComponents
    with HikariCPComponents {

  applicationEvolutions.start()

  override def dynamicEvolutions: DynamicEvolutions = new DynamicEvolutions

  implicit val transactor: Transactor[Task] =
    DataSourceTransactor[Task](dbApi.database("default").dataSource)

  implicit val wsClient: WSClient = NingWSClient()

  val applicationController = new ApplicationController
  val exercisesController = new ExercisesController
  val userController = new UserController
  val oauthController = new OAuthController
  val userProgressController = new UserProgressController

  val assets = new _root_.controllers.Assets(httpErrorHandler)

  val router = new Routes(
    httpErrorHandler,
    applicationController,
    userController,
    exercisesController,
    assets,
    oauthController,
    userProgressController
  )

  applicationLifecycle.addStopHook({ () ⇒
    Future(wsClient.close())
  })
}
