import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    "org.apache.httpcomponents"   %  "httpclient"                 % "4.5.7",
    "org.apache.httpcomponents"   %  "httpmime"                   % "4.5.7",
    "io.megl"                     %% "play-json-extra"            % "2.4.3",
    "uk.gov.hmrc"                 %% "bootstrap-play-25"          % "4.9.0",
    "uk.gov.hmrc"                 %% "govuk-template"             % "5.30.0-play-25",
    "uk.gov.hmrc"                 %% "play-json-union-formatter"  % "1.5.0",
    "uk.gov.hmrc"                 %% "play-reactivemongo"         % "6.2.0",
    "uk.gov.hmrc"                 %% "play-scheduling"            % "5.4.0",
    "uk.gov.hmrc"                 %% "play-ui"                    % "7.37.0-play-25"
  )

  private lazy val scope = "test, it"

  val test: Seq[ModuleID] = Seq(
    "com.github.tomakehurst"  %  "wiremock"                 % "2.20.0"                % scope,
    "com.typesafe.play"       %% "play-test"                % current                 % scope,
    "org.mockito"             %  "mockito-core"             % "2.25.1"                % scope,
    "org.pegdown"             %  "pegdown"                  % "1.6.0"                 % scope,
    "org.scalatest"           %% "scalatest"                % "3.0.4"                 % scope,
    "org.scalaj"              %% "scalaj-http"              % "2.4.1"                 % scope,
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "2.0.1"                 % scope,
    "uk.gov.hmrc"             %% "hmrctest"                 % "3.6.0-play-25"         % scope,
    "uk.gov.hmrc"             %% "reactivemongo-test"       % "3.1.0"                 % scope,
    "uk.gov.hmrc"             %% "service-integration-test" % "0.6.0-play-25"         % scope
  )

}
