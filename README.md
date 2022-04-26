# [ALPHA] Tuya Wall Mount Thermostat (Water/Electric Floor Heating) Zigbee driver


The latest (hopefully) stable version of the driver can be downloaded from this link: https://raw.githubusercontent.com/kkossev/Hubitat-Tuya-Wall-Thermostat/main/Tuya-Wall-Thermostat.groovy

As this project is a work-in-progress, the last hot fixes and new features are available in the development branch: 

https://raw.githubusercontent.com/kkossev/Hubitat-Tuya-Wall-Thermostat/development/Tuya-Wall-Thermostat.groovy

## Supported models

### Model 1 (AVATTO)
![image](https://user-images.githubusercontent.com/6189950/148385546-9e846840-8adb-4f3d-bbaf-41d549eab66f.png)
(AE [link](https://www.aliexpress.com/item/1005003575320865.html))
(Amazon.de [link](https://www.amazon.de/-/en/Thermostat-Temperature-Controller-Intelligent-Underfloor/dp/B09H6T9N9T?th=1))
Driver status: everything working (as this is the author's thermostat :) ) 

### Model 2 (MOES)
![image](https://user-images.githubusercontent.com/6189950/148380562-48506c2c-5fcf-4a68-826b-d725d5dc872a.png)

(AE [link](https://www.aliexpress.com/item/1005001891838308.html))
Driver status:  confirmed to  be working OK

### Model 3 (testing)
![image](https://user-images.githubusercontent.com/6189950/148374673-f2a86684-90f1-4af7-b0bb-9624f1a565af.png)
(AE [link](https://www.aliexpress.com/item/4001326539649.html))
Driver status:  waiting for confirmation 

### Model 4 (BEOK)
![image|343x349](upload://uiUFWI8zMxvqxiHz8nYeRvzFH21.png)


[(Beok Controls site link)](https://www.beok-controls.com/room-thermostat/)
Driver status:  confirmed to  be working OK

## Note
While the same driver **may** work with other Tuya thermostat models (different than these listed below), this is not guaranteed because of the commands differences between the  models and manufacturers.

## Compatibility
* Hubitat Elevation dashboards
* Hubitat mobile app (to be tested!)
* Amazon Alexa (to be tested!)
* Google Home (partially tested)

## Features

Currently, not all of the functionalities and settings that are available from Tuya SmartLife app for the specific model are implemented into this HE driver. 
The basic functions that are working at the moment are:
* Synchronizes the thermostat clock to HE hub time and day of the week.
* Switches the thermostat On and Off (thermostatMode).
* Reads the thermostat temperature sensor (temperature).
* Sets and reports the thermostat target temperature (heatingSetpoint).
* Sets and reports the thermostat operation mode ('manual' or 'scheduled').
* Reports the thermostat actual operating state  ('idle' or 'heating') - relay open or closed state.
* Reports the PID algorithm output variable as calculated by this simple formula ![image](https://user-images.githubusercontent.com/6189950/148383607-aba151f6-5a0c-4209-b271-d2a82d76001c.png) (AVATTO model only)

The driver adds some extra options and features:

* Automatic or manual selection of the thermostat group
* 'Force Manual' option - switches back the thermostat into 'manual' operation mode if it was accidentally put into 'scheduled' mode. Default is off.
* 'Resend Failed' option - resends the commands for setting up the thermostat setPoint and mode, if failed by any reason.
* Debug and Text info options. The debugging option is switched off automatically after 30 minutes.
* Minimum and maximum limits for the heating setpoint.
