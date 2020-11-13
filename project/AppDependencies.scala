import play.core.PlayVersion
import sbt._

object AppDependencies {

  private val httpComponentsVersion = "4.5.8"

  val compile: Seq[ModuleID] = Seq(
    "org.apache.httpcomponents"   %  "httpclient"                 % httpComponentsVersion,
    "org.apache.httpcomponents"   %  "httpmime"                   % httpComponentsVersion,
    "uk.gov.hmrc"                 %% "bootstrap-frontend-play-27" % "2.24.0",
    "uk.gov.hmrc"                 %% "govuk-template"             % "5.55.0-play-27",
    "uk.gov.hmrc"                 %% "play-json-union-formatter"  % "1.12.0-play-27",
    "uk.gov.hmrc"                 %% "simple-reactivemongo"       % "7.30.0-play-27",
    "uk.gov.hmrc"                 %% "play-scheduling-play-27"    % "7.10.0",
    "uk.gov.hmrc"                 %% "play-ui"                    % "8.11.0-play-27",
    "org.reactivemongo"           %% "reactivemongo-akkastream"   % "0.20.1",
    "com.lightbend.akka"          %% "akka-stream-alpakka-csv"    % "1.1.2",
    "com.typesafe.akka"           %% "akka-http"                  % "10.0.15",
    "com.typesafe.akka"           %% "akka-stream"                % "2.5.31",
    "org.typelevel"               %% "cats-core"                  % "2.2.0",
    "com.github.javafaker"        %  "javafaker"                  % "1.0.2"
  )

  private lazy val scope = "test, it"

  val test = Seq(
    "com.github.tomakehurst"     % "wiremock-jre8"          % "2.27.1"            % scope,
    "com.typesafe.play"         %% "play-test"              % PlayVersion.current % scope,
    "org.mockito"                % "mockito-core"           % "2.28.2"            % scope,
    "org.pegdown"                % "pegdown"                % "1.6.0"             % scope,
    "org.jsoup"                  % "jsoup"                  % "1.13.1"            % scope,
    "org.scalacheck"            %% "scalacheck"             % "1.14.3"            % scope,
    "org.scalaj"                %% "scalaj-http"            % "2.4.2"             % scope,
    "org.scalatest"             %% "scalatest"              % "3.0.8"             % scope,
    "org.scalatestplus.play"    %% "scalatestplus-play"     % "3.1.3"             % scope,
    "uk.gov.hmrc"               %% "reactivemongo-test"     % "4.21.0-play-27"    % scope
  )

}
