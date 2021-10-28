package utils

import com.typesafe.config.ConfigFactory

object ApplicationConfig {
  object App {
    val conf = ConfigFactory.defaultApplication()

    object Persistence {
      val enabled = conf.getBoolean("app.persistence.enabled")
      val path = conf.getString("app.persistence.filePath")
    }

    val numberOfSources: Int = conf.getInt("app.datasource.numberOfSources")
    val throttle = conf.getBoolean("app.datasource.throttle")
    val rate = conf.getInt("app.datasource.rate")
  }
}
