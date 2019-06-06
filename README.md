
# binding-tariff-admin-frontend

The Admin Front end Service for the Binding Tariff Suite of Applications

### Running

##### To run this Service you will need:

1) [Service Manager](https://github.com/hmrc/service-manager) Installed
2) [SBT](https://www.scala-sbt.org) Version `>0.13.13` Installed

##### Starting the application:
 
1) Run All Dependent Applications `sm --start DIGITAL_TARIFF_DEPS -r`
2) Run the backend `sm --start BINDING_TARIFF_CLASSIFICATION -r`
3) Run the filestore `sm --start BINDING_TARIFF_FILESTORE -r`

Finally Run `sbt run` to boot the app

Open `http://localhost:9584/binding-tariff-admin-frontend`
 
##### Starting With Service Manager

This application runs on port 9584

Run `sm --start BINDING_TARIFF_ADMIN_FRONTEND -r`

Open `http://localhost:9584/binding-tariff-admin`

##### Authentication

This service uses basic authentication. The password expires every year on January 1st and will need refreshing in configuration.

### Testing

Run `./run_all_tests.sh`

or `sbt test it:test`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
