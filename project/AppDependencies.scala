import play.core.PlayVersion
import sbt._

object AppDependencies {

  private val httpComponentsVersion = "4.5.8"

  val compile: Seq[ModuleID] = Seq(
    "org.apache.httpcomponents"   %  "httpclient"                 % httpComponentsVersion,
    "org.apache.httpcomponents"   %  "httpmime"                   % httpComponentsVersion,
    "uk.gov.hmrc"                 %% "bootstrap-play-26"          % "1.7.0",
    "uk.gov.hmrc"                 %% "govuk-template"             % "5.54.0-play-26",
    "uk.gov.hmrc"                 %% "play-json-union-formatter"  % "1.10.0-play-26",
    "uk.gov.hmrc"                 %% "simple-reactivemongo"       % "7.30.0-play-26",
    "uk.gov.hmrc"                 %% "play-scheduling"            % "7.4.0-play-26",
    "uk.gov.hmrc"                 %% "play-ui"                    % "8.8.0-play-26",
    "org.reactivemongo"           %% "reactivemongo-akkastream"   % "0.20.1",
    "com.lightbend.akka"          %% "akka-stream-alpakka-csv"    % "1.1.2",
    "com.typesafe.akka"           %% "akka-http"                  % "10.0.12",
    "com.typesafe.akka"           %% "akka-stream"                % "2.5.8",
    "org.typelevel"               %% "cats-core"                  % "2.0.0",
    "org.typelevel"               %% "alleycats-core"             % "2.0.0",
    "com.github.javafaker"        %  "javafaker"                  % "1.0.2"
  )

  private lazy val scope = "test, it"

  val test = Seq(
    "com.github.tomakehurst"     % "wiremock-jre8"          % "2.26.1"            % scope,
    "com.typesafe.play"         %% "play-test"              % PlayVersion.current % scope,
    "org.mockito"                % "mockito-core"           % "2.26.0"            % scope,
    "org.pegdown"                % "pegdown"                % "1.6.0"             % scope,
    "org.jsoup"                  % "jsoup"                  % "1.13.1"            % scope,
    "org.scalacheck"            %% "scalacheck"             % "1.14.3"            % scope,
    "org.scalaj"                %% "scalaj-http"            % "2.4.1"             % scope,
    "org.scalatest"             %% "scalatest"              % "3.0.8"             % scope,
    "org.scalatestplus.play"    %% "scalatestplus-play"     % "3.1.3"             % scope,
    "uk.gov.hmrc"               %% "hmrctest"               % "3.9.0-play-26"     % scope,
    "uk.gov.hmrc"               %% "reactivemongo-test"     % "4.21.0-play-26"    % scope
  )

}
