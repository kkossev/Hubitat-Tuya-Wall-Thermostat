/**
 *  Tuya Wall Thermostat driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/release-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050 
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *    in compliance with the License. You may obtain a copy of the License at:
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *    on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *    for the specific language governing permissions and limitations under the License.
 * 
 *  Credits: Jaewon Park, iquix and many others
 * 
 * ver. 1.0.0 2022-01-09 kkossev  - Inital version
 * ver. 1.0.1 2022-01-09 kkossev  - modelGroupPreference working OK
 * ver. 1.0.2 2022-01-09 kkossev  - MOES group heatingSetpoint and setpointReceiveCheck() bug fixes
 * ver. 1.0.3 2022-01-10 kkossev  - resending heatingSetpoint max 3 retries; heatSetpoint rounding up/down; incorrect temperature reading check; min and max values for heatingSetpoint
 * ver. 1.0.4 2022-01-11 kkossev  - reads temp. calibration for AVATTO, patch: temperatures > 50 are divided by 10!; AVATO parameters decoding; added BEOK model
 * ver. 1.0.5 2022-01-15 kkossev  - 2E+1 bug fixed; added rxCounter, txCounter, duplicateCounter; ChildLock control; if boost (emergency) mode was on, then auto() heat() off() commands cancel it;
 *                                  BRT-100 thermostatOperatingState changes on valve report; AVATTO/MOES switching from off mode to auto/heat modes fix; command 'controlMode' is now removed.
 * ver. 1.0.6 2022-01-16 kkossev  - debug/trace commands fixes
 * ver. 1.0.7 2022-03-21 kkossev  - added childLock attribute and events; checkDriverVersion(); removed 'Switch' capability and events; enabled 'auto' mode for all thermostat types.
 * ver. 1.0.8 2022-04-03 kkossev  - added tempCalibration; hysteresis; minTemp and maxTemp for AVATTO and BRT-100; added Battery capability for BRT-100
 * ver. 1.2.1 2022-04-05 kkossev  - BRT-100 basic cluster warning supressed; tempCalibration, maxTemp, minTemp fixes; added Battery capability; 'Changed from device Web UI' desctiption in off() and heat() events.
 * ver. 1.2.2 2022-09-04 kkossev  - AVATTO additional DP logging; removed Calibration command (now is as Preference parameter); replaced Initialize capability w/ custom command; degrees symbol in temp. unit;
 *                                  Refresh command wakes up the display';  Google Home compatibility
 * ver. 1.2.3 2022-09-05 kkossev  - added FactoryReset command (experimental, change Boolean debug = true); added AVATTO programMode preference; 
 * ver. 1.2.4 2022-09-28 kkossev  - _TZE200_2ekuz3dz fingerprint corrected
 * ver. 1.2.5 2022-10-08 kkossev  - BEOK: added sound on/off, tempCalibration, hysteresis, tempCeiling, setBrightness, 0.5 degrees heatingSetpoint (BEOK only); bug fixes for BEOK: Child lock, thermostatMode, operatingState
 * ver. 1.2.6 2022-10-16 kkossev  - BEOK: time sync workaround; BEOK: temperature scientific representation bug fix; parameters number/decimal fixes; brightness and maxTemp bug fixes; heatingTemp is always rounded to 0.5; cool() does not switch the thermostat off anymore
 * ver. 1.2.7 2022-11-05 kkossev  - BEOK: added frostProtection; BRT-100: tempCalibration bug fix; reversed heat and auto modes for MOES dp=3; hysteresis is hidden for BRT-100; maxTemp lower limit set to 15; dp3 is ignored from MOES/BSEED if in off mode
 *                                  supressed dp=9 BRT-100 unknown function warning; 
 * ver. 1.2.8 2022-11-27 kkossev  - added 'brightness' attribute; removed MODEL3; dp=3 refactored; presence function bug fix; added resetStats command; refactored stats; faster sending of Zigbee commands; time is synced every hour for BEOK;
 *                                  modeReceiveCheck() and setpointReceiveCheck() refactored; 
 * ver. 1.2.9 2022-12-05 kkossev  - bugfix: 'supportedThermostatFanModes' and 'supportedThermostatModes' proper JSON formatting; homeKitCompatibility option
 * ver. 1.2.10 2023-01-08 kkossev  - bugfix: AVATTO thermostat can not be switched off from HE dashboard;
 * ver. 1.2.11 2023-01-14 kkossev  - bugfix: BEOK setBrightness retry
 * ver. 1.3.0  2023-06-03 kkossev  - added sensorSelection; replaced Presence w/ Health Status; added ping() and rtt; added '--- Select ---' default value for the sensorSelection command; added sensorSelection as attribute
 * ver. 1.3.1  2023-10-29 kkossev  - (dev. branch) - added 'HY369' group (TS0601 _TZE200_ckud7u2l); add state.deviceProfile
 * ver. 1.3.2  2023-11-16 kkossev  - (dev. branch) - added TS0601 _TZE200_bvrlmajk Avatto TRV07 
 *
 *                                  TODO: parse multiple Tuya DPs in one message;
 *                                  TODO: add option to send digital heatingSetpoint events every hour if no updates are received from the device;
 *                                  TODO: remove homeKitCompatibility option;
 *                                  TODO: HY369 mode processing (dp=4)
 *                                  TODO: tuyaAppVersion in Data section
 *                                  TODO: duplicate check for temperature reports is wrong ! (BEOK sends temp report every 6 seconds ! )
 *                                  TODO: https://community.hubitat.com/t/release-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050/68?u=kkossev 
 *                                  TODO: add forceOn; 
 *                                  TODO: @user2669  _TZE200_aoclfnxz (MOES) : https://community.hubitat.com/t/release-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050/60?u=kkossev
 *                                  TODO: @user2669 https://community.hubitat.com/t/release-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050/65?u=kkossev 
 *
*/

def version() { "1.3.2" }
def timeStamp() {"2023/11/16 14:28 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol
import java.text.DecimalFormat
import groovy.time.TimeCategory


@Field static final Boolean debug = false

metadata {
    definition (name: "Tuya Wall Thermostat", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat-Tuya-Wall-Thermostat/development/Tuya-Wall-Thermostat.groovy", singleThreaded: true ) {
        capability "Actuator"
        capability "Refresh"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Thermostat"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatCoolingSetpoint"
        capability "ThermostatOperatingState"
        capability "ThermostatSetpoint"
        capability "ThermostatMode"
        capability "Battery"
        capability "HealthCheck"
        
        attribute "childLock", "enum", ["off", "on"]
        attribute "brightness", "enum", ['off', 'low', 'medium', 'high']
        attribute "healthStatus", "enum", ["offline", "online", "unknown"]
        attribute "sensorSelection", "enum", sensorOptions.values() as List<String>
        attribute "rtt", "number" 
        
        if (_DEBUG == true) {
            command "zTest", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] 
            ]
            command "test"
        }
        command "initialize", [[name: "Initialize the thermostat after switching drivers.  \n\r   ***** Will load device default values! *****" ]]
        command "childLock",  [[name: "ChildLock", type: "ENUM", constraints: ["off", "on"], description: "Select Child Lock mode"] ]
        command "setBrightness",  [[name: "setBrightness", type: "ENUM", constraints: ["off", "low", "medium", "high"], description: "Set LCD brightness for BEOK thermostats"] ]
        command "sensorSelection",  [[name: "sensorSelection", type: "ENUM", constraints: ["99":"--- Select ---"] + sensorOptions, description: "Select the temperature sensor"] ]
        
        command "factoryReset", [[name:"factoryReset", type: "STRING", description: "Type 'YES'", constraints: ["STRING"]]]
        command "resetStats", [[name: "Reset Statistics" ]]
        
        
        // (AVATTO)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ye5jkfsb",  deviceJoinName: "AVATTO Wall Thermostat" // ME81AH 
        // (Moes)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_aoclfnxz",  deviceJoinName: "Moes Wall Thermostat" // BHT-002
        // (unknown)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_unknown",  deviceJoinName: "_TZE200_ Thermostat" // unknown
        // (BEOK)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_2ekuz3dz",  deviceJoinName: "Beok Wall Thermostat" // X5H-GB-B 
        // (BRT-100 for dev tests only!)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_b6wax7g0",  deviceJoinName: "BRT-100 TRV" // BRT-100
        //fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_chyvmhay",  deviceJoinName: "Lidl Silvercrest" // Lidl Silvercrest (dev tests only)
        //
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ckud7u2l",  deviceJoinName: "Moes HY369 TRV" // added 10/29/2023
        // Avatto TRV07
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_bvrlmajk",  deviceJoinName: "AVATTO TRV07"  // added 11/14/2023 // https://community.hubitat.com/t/release-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050/215?u=kkossev
        
    }
    preferences {
        if (logEnable == true || logEnable == false) { 
            input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: false)
            input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
            input (name: "forceManual", type: "bool", title: "<b>Force Manual Mode</b>", description: "<i>If the thermostat changes into schedule mode, then it automatically reverts back to manual mode</i>", defaultValue: false)
            input (name: "resendFailed", type: "bool", title: "<b>Resend failed commands</b>", description: "<i>If the thermostat does not change the Setpoint or Mode as expected, then commands will be resent automatically</i>", defaultValue: false)
            input (name: "minTemp", type: "number", title: "<b>Minimum Temperature</b>", description: "<i>The Minimum temperature setpoint that can be sent to the device</i>", defaultValue: 10, range: "0..60")
            input (name: "maxTemp", type: "number", title: "<b>Maximum Temperature</b>", description: "<i>The Maximum temperature setpoint that can be sent to the device</i>", defaultValue: 35, range: "15..95")
            input (name: "modelGroupPreference", title: "Select a model group. Recommended value is <b>'Auto detect'</b>", /*description: "<i>Thermostat type</i>",*/ type: "enum", options:["Auto detect":"Auto detect", "AVATTO":"AVATTO", "MOES":"MOES", "BEOK":"BEOK", "BRT-100":"BRT-100", "HY369":"HY369", "TRV07":"TRV07"], defaultValue: "Auto detect", required: false)        
            input (name: "tempCalibration", type: "decimal", title: "<b>Temperature Calibration</b>", description: "<i>Adjust measured temperature range: -9..9 C</i>", defaultValue: 0.0, range: "-9.0..9.0")
            if (!(getModelGroup() in ['BRT-100'])) {
                input (name: "hysteresis", type: "decimal", title: "<b>Hysteresis</b>", description: "<i>Adjust switching differential range: 0.5 .. 5.0 C</i>", defaultValue: 1.0, range: "0.5..5.0")        // not available for BRT-100 !
            }
            if (isBEOK()) {
                input (name: "tempCeiling", type: "number", title: "<b>Temperature Ceiling</b>", description: "<i>temperature limit parameter (unknown functionality) ></i>", defaultValue: 35, range: "35..95")    // step is 5 deg. for BEOK'; default 35?
                input (name: "brightness", type: "enum", title: "<b>LCD brightness</b>", description:"<i>LCD brightness control</i>", defaultValue: '3', options: brightnessOptions)
            }
            if (getModelGroup() in ['AVATTO'])  {
                input (name: "programMode", type: "enum", title: "<b>Program Mode</b> (thermostat internal schedule)", description: "<i>Recommended selection is '<b>off</b>'</i>", defaultValue: 0, options: [0:"off", 1:"Mon-Fri", 2:"Mon-Sat", 3: "Mon-Sun"])
            }
            if (isBEOK())  {
               input (name: "frostProtection", type: "bool", title: "<b>Disable/Enable frost protection</b>", description: "<i>Disable/Enable frost protection</i>", defaultValue: true)
               input (name: "sound", type: "bool", title: "<b>Disable/Enable sound</b>", description: "<i>Disable/Enable sound</i>", defaultValue: true)
            }
            input (name: "homeKitCompatibility",  type: "bool", title: "<b>HomeKit Compatibility</b>",  description: "Enable/disable HomeKit Compatibility", defaultValue: false)
        }
    }
}



@Field static final Map<String, String> Models = [
    '_TZE200_ye5jkfsb'  : 'AVATTO',      // Tuya AVATTO ME81AH 
    '_TZE200_aoclfnxz'  : 'MOES',        // Tuya Moes BHT series Thermostat BTH-002  (also BSEED)
    '_TZE200_2ekuz3dz'  : 'BEOK',        // Beok thermostat
    '_TZE200_b6wax7g0'  : 'BRT-100',     // TRV BRT-100; MOES, ZONNSMART
    '_TYST11_ckud7u2l'  : 'HY369',       // Moes HY369         // https://github.com/jacekk015/zha_quirks/blob/main/ts0601_trv_moes.py#L38 
    '_TZE200_ckud7u2l'  : 'HY369',       // KKmoon Tuya; temp /10.0  
                                         // vendor: Hysen model: HY369-ZB    /
                                         // https://www.aliexpress.com/item/4000742201198.html    
                                         // https://github.com/zigbeefordomoticz/wiki/blob/38206248debb348e40e3718d07b4e0ec5baa454c/en-eng/Technical/Tuya-0xEF00.md?plain=1#L102  
    '_TZE200_ywdxldoj'  : 'HY369',       // 
    '_TZE200_cwnjrr72'  : 'HY369',       // 
    '_TZE200_pvvbommb'  : 'HY369',       // 
    '_TZE200_2atgpdho'  : 'HY369',       // added 10/30/2023
    '_TZE200_bvrlmajk'  : 'TRV07',       // added 11/14/2023    // https://github.com/OzGav/zigbee-herdsman-converters/blob/d2e4e8ed359163ea8553fcae1cf2fa945b0f4c62/devices/tuya.js#L2194 
    '_TZE200_zion52ef'  : 'TEST3',       // TRV MOES => fn = "0001 > off:  dp = "0204"  data = "02" // off; heat:  dp = "0204"  data = "01" // on; auto: n/a !; setHeatingSetpoint(preciseDegrees):   fn = "00" SP = preciseDegrees *10; dp = "1002"
    '_TZE200_c88teujp'  : 'TEST3',       // TRV "SEA-TR", "Saswell", model "SEA801" (to be tested)
    '_TZE200_xxxxxxxx'  : 'UNKNOWN',     
    ''                  : 'UNKNOWN'      // 
]

def isBEOK()       { return device.getDataValue('manufacturer') in ['_TZE200_2ekuz3dz'] }
def isMOES()       { return device.getDataValue('manufacturer') in ['_TZE200_aoclfnxz'] }
def isBSEED()      { return isMOES() }
def isTRV07()      { return device.getDataValue('manufacturer') in ['_TZE200_bvrlmajk'] }

@Field static final Map brightnessOptions = [
    '0' : 'off',
    '1' : 'low',
    '2' : 'medium',
    '3' : 'high'
]

@Field static final Map faultOptions = [
    '0' : 'none',
    '1' : 'e1',
    '2' : 'e2',
    '3' : 'e3'
]

@Field static final Map sensorOptions = [    // BEOK - only in and out; AVATTO + both
    '0' : 'in',
    '1' : 'out',
    '2' : 'both'
]

@Field static final Map programModeOptions = [
    '0' : 'off',
    '1' : 'Mon-Fri',
    '2' : 'Mon-Sat',
    '3' : 'Mon-Sun'
]

@Field static final Integer presenceCountTreshold = 3
@Field static final Integer defaultPollingInterval = 3600
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MaxRetries = 5
@Field static final Integer NOT_SET = -1
                                
private getCLUSTER_TUYA()       { 0xEF00 }
private getSETDATA()            { 0x00 }
private getSETTIME()            { 0x24 }

// Tuya Commands
private getTUYA_REQUEST()       { 0x00 }
private getTUYA_REPORTING()     { 0x01 }
private getTUYA_QUERY()         { 0x02 }
private getTUYA_STATUS_SEARCH() { 0x06 }
private getTUYA_TIME_SYNCHRONISATION() { 0x24 }

// tuya DP type
private getDP_TYPE_RAW()        { "01" }    // [ bytes ]
private getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ]
private getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ]
private getDP_TYPE_STRING()     { "03" }    // [ N byte string ]
private getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ]
private getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits


// Parse incoming device messages to generate events
def parse(String description) {
    checkDriverVersion()
    unschedule('deviceCommandTimeout')    
    incRxCtr()
    setHealthStatusOnline()    // was     setPresent()
    //if (settings?.logEnable) log.debug "${device.displayName} parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap?.clusterInt == zigbee.BASIC_CLUSTER && descMap.attrInt == 0x01) {
            logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})"
            def now = new Date().getTime()
            Map lastTxMap = stringToJsonMap( state.lastTx )
            def timeRunning = now.toInteger() - (lastTxMap.pingTime ?: '0').toInteger()
            if (timeRunning < MAX_PING_MILISECONDS) {
                sendRttEvent()
            } 
        }
        else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
            logDebug "time synchronization request from device, descMap = ${descMap}"
            syncTuyaDateTime()
        } 
        else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
            String clusterCmd = descMap?.data[0]
            def status = descMap?.data[1]            
            if (settings?.logEnable) log.debug "${device.displayName} device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
            setLastRx( NOT_SET, NOT_SET)    // -1
            if (status != "00") {
                if (settings?.logEnable) log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} group = ${getModelGroup()} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
            }
            
        } 
        else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02")) {
            def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
            def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
            def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
            def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
            if (isDuplicated( dp, fncmd )) {
                if (settings?.logEnable) log.debug "(duplicate) transid=${transid} dp_id=${dp_id} <b>dp=${dp}</b> fncmd=${fncmd} command=${descMap?.command} data = ${descMap?.data}"
                //if ( state.duplicateCounter != null ) state.duplicateCounter = state.duplicateCounter +1
                incDupeCtr()
                return
            }
            else {
                if (settings?.logEnable) log.debug "${device.displayName} dp_id=${dp_id} <b>dp=${dp}</b> fncmd=${fncmd}"
                setLastRx( dp, fncmd)
            }
            // the switch cases below default to dp_id = "01"
            switch (dp) {
                case 0x01 :  // (01) switch state : On / Off
                    switch (getModelGroup()) {
                        case 'AVATTO' :    // all wall thermostats: AVATTO switch (boolean) // ; BEOK x5hState; MOES/BEOK
                        case 'MOES' :
                        case 'BEOK' :
                            def switchState = (fncmd == 0) ? "off" : state.lastThermostatMode
                            sendEvent(name: "thermostatMode", value: switchState)
                            if (switchState == "off") {
                                logInfo "switchState reported is: OFF"
                                sendEvent(name: "thermostatOperatingState", value: "idle")
                            }
                            else {
                                if (settings?.logEnable) {log.info "${device.displayName} switchState reported is: ON, restoring last lastThermostatMode ${state.lastThermostatMode} (dp=${dp}, fncmd=${fncmd})"}
                                sendEvent(name: "thermostatOperatingState", value: state.lastThermostatOperatingState)
                            }                        
                            if (switchState == getLastMode())  {
                                logDebug "last sent mode ${getLastMode()} is confirmed from the device (dp=${dp}, fncmd=${fncmd})"
                            }
                            else {
                                logWarn "last sent mode ${getLastMode()} DIFFERS from the mode received from the device ${switchState} (dp=${dp}, fncmd=${fncmd})"
                            }
                            break
                        case 'BRT-100' :    // 0x0401 # Mode (Received value 0:Manual / 1:Holiday / 2:Temporary Manual Mode / 3:Prog)
                        case 'TRV07' :      // 
                            logDebug "${device.displayName} mode is ${fncmd} (<b>dp=${dp}</b> fncmd=${fncmd})"
                            def thermostatModes = ["auto", "heating", "off", "on"] // using "heating" for consistency, 01 is defined as manual in documentation
                            def thermostatMode = thermostatModes[fncmd]
                            sendEvent(name: "thermostatMode", value: thermostatMode)
                            break
                        case 'TEST3' :
                            processBRT100Presets( dp, fncmd )
                            break
                        case 'HY369' :  // dp1 not used 
                        default :
                            if (settings?.logEnable) {log.warn "${device.displayName} Thermostat model group ${getModelGroup()} is not processed! (dp=${dp}, fncmd=${fncmd})"}
                            break
                    }                   
                    break
                case 0x02 : // thermostatMode (AVATTO,BEOK, LIDL)
                    switch (getModelGroup()) {
                        case 'AVATTO' :    // AVATTO : mode (enum) 'manual', 'program'; 
                        case 'BEOK' :     // BEOK: x5hMode
                            logDebug "AVATTO/BEOK current thermostatMode was ${device.currentState('thermostatMode').value}"
                            /* was commented out 1/27/2022 - breaks switching off from HE dashboard! */
                            if (device.currentState("thermostatMode").value == "off") {
                                logWarn "ignoring 0x02 command in off mode"
                                sendEvent(name: "thermostatOperatingState", value: "idle")
                                break    // ignore 0x02 command if thermostat was switched off !!
                            }
                            else {    // previous thermosatMode was heat or auto
                            /**/
                                logDebug "previous thermosatMode was  ${device.currentState('thermostatMode').value}..."
                                def thermostatMode = fncmd == 0 ? "heat" : "auto"    // inverted!
                                if (thermostatMode == "auto") {
                                    if (settings?.forceManual == true) {
                                        logDebug "'Force Manual Mode' preference option is enabled, switching back to heat mode!"
                                        setManualMode()
                                    }
                                }
                                if (settings?.logEnable) {log.info "${device.displayName} Thermostat mode reported is: <b>${thermostatMode}</b> (dp=${dp}, fncmd=${fncmd})"}
                                else if (settings?.txtEnable) {log.info "${device.displayName} Thermostat mode reported is: <b>${thermostatMode}</b>"}
                                sendEvent(name: "thermostatMode", value: thermostatMode)
                                state.lastThermostatMode = thermostatMode
                            } 
                            break
                        case 'MOES' :    // MOES thermostatMode: 0-manual; 1:auto; 2:auto w/ temporary changed setpoint
                            def mode
                            if (fncmd != 0) {     
                                mode = "auto"    // scheduled
                                if (settings?.forceManual == true) {
                                    if (settings?.txtEnable) log.warn "${device.displayName} 'Force Manual Mode' preference option is enabled, switching back to heat mode!"
                                    setManualMode()
                                }
                            } 
                            else {
                                mode = "heat"    // manual
                            }
                            logDebug "BEOK/MOES (dp=2) thermostatMode = ${mode}"                        
                            if (settings?.logEnable) {log.info "${device.displayName} BEOK/MOES  (dp=2) thermostatMode reported is: $mode (dp=${dp}, fncmd=${fncmd})"}
                            else if (settings?.txtEnable) {log.info "${device.displayName} BEOK/MOES (dp=2) thermostatMode reported is: ${mode}"}
                            sendEvent(name: "thermostatMode", value: mode)
                            state.lastThermostatMode = mode 
                            break    // no more processing for BEOK!
                        case 'BRT-100' :    // BRT-100 Thermostat heatsetpoint # 0x0202 #
                        case 'HY369' :
                        case 'TRV07' :      // TODO - check TRV07 "Target temperature" !!!!!!!!!!!
                        case 'TEST3' :
                            processTuyaHeatSetpointReport( fncmd )              // target temp, in degrees (int!)
                            break
                        default :
                            if (settings?.logEnable) {log.warn "${device.displayName} Thermostat model group ${getModelGroup()} is not processed! (dp=${dp}, fncmd=${fncmd})"}
                            break
                    }                   
                    break
                case 0x03 :    // BEOK x5hWorkingStatus (thermostatOperatingState) !
                    logDebug "processing command dp=${dp} fncmd=${fncmd} (lastThermostatMode=${state.lastThermostatMode})" // TODO: See which models this is actually there for, and move it to said model only
                    switch (getModelGroup()) {
                        case 'AVATTO' :
                        case 'BEOK' :
                            def thermostatOperatingState = (fncmd == 1) ? "heating" : "idle"
                            sendThermostatOperatingStateEvent(thermostatOperatingState)    // "thermostatOperatingState"
                            if (settings?.logEnable) {log.info "${device.displayName} Thermostat working status (thermostatOperatingState) reported is: ${thermostatOperatingState} (dp=${dp}, fncmd=${fncmd})"}
                            else if (settings?.txtEnable) {log.info "${device.displayName} Thermostat working status (thermostatOperatingState) reported is: ${thermostatOperatingState}"}
                            break
                        case 'MOES' :
                            if (settings?.logEnable) {log.warn "${device.displayName} IGNORING dp=${dp}, fncmd=${fncmd} command for BSEED/MOES while in <b>${device.currentValue("thermostatMode", true)}</b> thermostatMode!"}
                            break    // shouldn't come here ... TODO!
                        case 'BRT-100' :
                        case 'TEST3' :    // Thermostat current temperature
                        case "TRV07" :    // added 11/14/2023
                            logDebug "processTuyaTemperatureReport descMap?.size() = ${descMap?.data.size()} dp_id=${dp_id} <b>dp=${dp}</b> :"
                            processTuyaTemperatureReport( fncmd )
                            break
                        case 'HY369' :
                            // TODO !!
                            // # [0] away [1] scheduled [2] manual [3] comfort [4] eco [5] boost [6] complex
                            logDebug "HY369 mode (dp=${dp}, fncmd=${fncmd}) <b>not processed</b>!"
                            break
                        default :
                            if (settings?.logEnable) {log.warn "${device.displayName} Thermostat model group ${getModelGroup()} is not processed! (dp=${dp}, fncmd=${fncmd})"}
                            break
                    }
                    break
                case 0x04 :    // BRT-100 Boost    DP_IDENTIFIER_THERMOSTAT_BOOST    DP_IDENTIFIER_THERMOSTAT_BOOST 0x04 // Boost for Moes
                    processTuyaBoostModeReport( fncmd )
                    // TODO 'HY369'  | 0x04       |           | Mode 0x01 Auto ,0x02 Off | |
                    break
                case 0x05 :    // BRT-100 ?
                    if (settings?.txtEnable) log.info "${device.displayName} configuration is done. Result: 0x${fncmd}"
                    break
                case 0x06 :
                    switch (getModelGroup()) {
                        case 'TRV07' :
                            if (settings?.txtEnable) {log.info "${device.displayName} thermostatOperatingState is: ${fncmd==1 ? 'heating' : 'idle'}"}
                            else if (settings?.logEnable) {log.info "${device.displayName} thermostatOperatingState is: ${fncmd==1 ? 'heating' : 'idle'} (dp=${dp}, fncmd=${fncmd})"}
                            sendThermostatOperatingStateEvent(fncmd==1 ? "heating" : "idle")
                            break
                        default :
                            if (settings?.txtEnable) {log.info "${device.displayName} thermostatOperatingState is: ${fncmd==0 ? 'heating' : 'idle'}"}
                            else if (settings?.logEnable) {log.info "${device.displayName} thermostatOperatingState is: ${fncmd==0 ? 'heating' : 'idle'} (dp=${dp}, fncmd=${fncmd})"}
                            sendThermostatOperatingStateEvent(fncmd==0 ? "heating" : "idle")
                            break
                    }

                    break
                case 0x07 :    // others Childlock status    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_1 0x07    // 0x0407 > starting moving     // sound for X5H thermostat
                    if (isBEOK()) {
                        logInfo "sound is: ${fncmd==0?'off':'on'}"
                        device.updateSetting( "sound",  [value:(fncmd==0?false:true), type:"bool"] )
                    }
                    else if (getModelGroup() == 'HY369') {
                        logInfo "HY369 Child Lock (dp=${dp}) is: ${fncmd}"    //  [0] unlocked [1] locked
                    }
                    else if (getModelGroup() in ['TRV07']) {    
                        logInfo "TRV07 Window status dp=${dp} fncmd=${fncmd}" // 
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} valve starts moving: 0x${fncmd}"    // BRT-100  00-> opening; 01-> closed!
                        if (fncmd == 00) {
                            sendThermostatOperatingStateEvent("heating")
                        }
                        else {    // fncmd == 01
                            sendThermostatOperatingStateEvent("idle")
                        }
                    }
                    break
                case 0x08 :     // DP_IDENTIFIER_WINDOW_OPEN2 0x08    // BRT-100 TRV07
                    logInfo "Open window detection MODE (dp=${dp}) is: ${fncmd}"    //0:function disabled / 1:function enabled
                    break
                case 0x09 :     // BRT-100 unknown function
                    logInfo "BRT-100 unknown function (dp=${dp}) is: ${fncmd}"
                    break
                case 0x0A :    // (10) BEOK - x5hFrostProtection
                    logInfo "frost protection is: ${fncmd==0?'off':'on'} (0x${fncmd})"
                    device.updateSetting( "frostProtection",  [value:(fncmd == 0) ? false : true, type:"bool"] )
                    break;
                case 0x0B :     // (12)
                    logInfo "TRV07 Child lock dp=${dp} fncmd=${fncmd}" // TRV07
                    sendEvent(name: "childLock", value: (fncmd == 0) ? "off" : "on" )
                    break
                case 0x0D :    // (13) 
                    if (getModelGroup() in ['TRV07']) {    
                        if (fncmd <= 200) {
                            logInfo "TRV07 battery dp=${dp} fncmd=${fncmd}" // ? fncmd=3281408 ?
                            def battery = fncmd > 100 ? 100 : fncmd
                            if (settings?.txtEnable) log.info "${device.displayName} battery is: ${fncmd} % (dp=${dp})"
                            getBatteryPercentageResult(battery*2)    
                        }
                        else {
                            logInfo "TRV07 bad value for a battery report dp=${dp} fncmd=${fncmd}" // ? fncmd=3281408 ?
                        }
                    }
                    else {     // BRT-100 Childlock status    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_4 0x0D MOES, LIDL, TRV07
                        logInfo "Child Lock (dp=${dp}) is: ${fncmd}"    // 0:function disabled / 1:function enabled
                        sendEvent(name: "childLock", value: (fncmd == 0) ? "off" : "on" )
                    }
                    break
                case 0x0E :    // (14) BRT-100 Battery # 0x020e # battery percentage (updated every 4 hours )
                    if (getModelGroup() in ['TRV07']) {    
                        logInfo "TRV07 Fault alarm dp=${dp} fncmd=${fncmd}" // TODO !!! ? fncmd=3281408 or 0 ?
                    }
                    else {
                        def battery = fncmd >100 ? 100 : fncmd
                        if (settings?.txtEnable) log.info "${device.displayName} battery is: ${fncmd} % (dp=${dp})"
                        getBatteryPercentageResult(fncmd*2)                
                    }
                    break
                case 0x0F :    // (15)
                    if (getModelGroup() in ['TRV07']) {    
                        logInfo "TRV07 Minimum limit temperature is: ${fncmd/10} (raw ${fncmd})"    // 5.0  TODO  - update preference
                    }                
                    else {
                        logInfo "unknown dp=${dp} fncmd=${fncmd}"
                    }
                case 0x10 :    // (16): Heating setpoint AVATTO; x5hSetTemp BEOK;   // TODO isTRV07() - maybe heatingSetpoint  ??? 
                    if (getModelGroup() in ['TRV07']) {    
                        logInfo "TRV07 Maximum limit temperature is: ${fncmd/10} (raw ${fncmd})"    // 30.0 TODO - update preference    
                    }
                    else {
                        processTuyaHeatSetpointReport( fncmd )
                    }
                    break
                case 0x11 :    // (17) AVATTO, TRV07
                    if (getModelGroup() in ['TRV07']) {
                        logDebug "TRV07 Heating schedule dp=${dp} fncmd=${fncmd}" // ? fncmd=3281408 ?
                    }
                    else {
                        logInfo "AVATTO Set temperature F is: ${fncmd}"
                    }
                    break
                case 0x12 :    // (18) Max Temp Limit MOES, LIDL
                    // TODO 'HY369' - windowdetection 
                    if (getModelGroup() in ['AVATTO'])  {
                        if (settings?.txtEnable) log.info "${device.displayName} Set temperature upper limit F is: ${fncmd}"
                    }
                    else if (getModelGroup() in ['TRV07']) {    
                        logDebug "TRV07 Week program Tuesday dp=${dp} fncmd=${fncmd}" // ? fncmd=3281408 ?
                    }
                    else {    // KK TODO - also Window open status (false:true) for TRVs ?    DP_IDENTIFIER_WINDOW_OPEN
                        if (settings?.txtEnable) log.info "${device.displayName} Max Temp Limit is: ${fncmd}"
                    }
                    break
                case 0x13 :    // (19) Max Temp LIMIT AVATTO MOES, LIDL
                    if (getModelGroup() in ['AVATTO']) {
                        device.updateSetting("maxTemp", [value: fncmd as int , type:"number"])
                        if (settings?.txtEnable) log.info "${device.displayName} AVATTO Max Temp Limit is: ${fncmd} C (dp=${dp}, fncmd=${fncmd})"
                    }
                    else if (isBEOK()) {
                        device.updateSetting("maxTemp", [value: fncmd as int , type:"number"])
                        if (settings?.txtEnable) log.info "${device.displayName} BEOK Max Temp Limit is: ${fncmd} C (dp=${dp}, fncmd=${fncmd})"
                    }
                    else if (getModelGroup() in ['TRV07']) {
                        logDebug "TRV07 Week program Wednesday dp=${dp} fncmd=${fncmd}"
                    }
                    else {
                        // TODO - MOES !!
                        logInfo "${getModelGroup()} Max Temp Limit is: ${fncmd} C (dp=${dp}, fncmd=${fncmd})"
                    }
                    break
                case 0x14 :    //  (20) Dead Zone Temp (hysteresis) MOES, LIDL
                    if (getModelGroup() in ['AVATTO'])  {
                        if (settings?.txtEnable) log.info "${device.displayName} lower limit F is: ${fncmd}"
                        // TODO - clarify!
                    }
                    else if (getModelGroup() == 'HY369') {
                        logInfo "HY369 valvestate (VALVE_DETECT_ATTR) is: ${fncmd}"   //  [0] do not report [1] report
                    }
                    else if (getModelGroup() in ['TRV07']) {    
                        logDebug "TRV07 Week program Thursday dp=${dp} fncmd=${fncmd}"
                    }
                    else {    // KK TODO - also Valve state report : on=1 / off=0 ?  DP_IDENTIFIER_THERMOSTAT_VALVE 0x14 // Valve
                        if (settings?.txtEnable) log.info "${device.displayName} Dead Zone Temp (hysteresis) is: ${fncmd}"
                        // TODO - clarify!
                    }
                    break
                case 0x15 :    // (21) 'HY369' battery 
                    if (getModelGroup() in ['TRV07']) {    
                        logDebug "TRV07 Week program Friday dp=${dp} fncmd=${fncmd}"
                    }
                    else {
                        def battery = fncmd >100 ? 100 : fncmd
                        if (settings?.txtEnable) log.info "${device.displayName} battery is: ${fncmd} % (dp=${dp})"
                        getBatteryPercentageResult(fncmd*2)                
                    }
                    break
                case 0x16 :    // (22) 
                    if (getModelGroup() in ['TRV07']) {    
                        logDebug "TRV07 Week program Saturday dp=${dp} fncmd=${fncmd}"
                    }
                    else {
                        logInfo "unknown dp=${dp} fncmd=${fncmd}"
                    }
                    break
                case 0x17 :    // (23) temperature scale for AVATTO
                    if (getModelGroup() in ['TRV07']) {    
                        logDebug "TRV07 Week program Sunday dp=${dp} fncmd=${fncmd}"
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} temperature scale is: ${fncmd==0?'C':'F'} (${fncmd})"
                    }
                    break
                case 0x18 :    // (24) : Current (local) temperature; x5hCurrentTemp BEOK TRV07
                    logDebug "processTuyaTemperatureReport dp_id=${dp_id} <b>dp=${dp}</b> :"
                    processTuyaTemperatureReport( fncmd )
                    break
                case 0x1A :    // (26) AVATTO setpoint lower limit
                    if (getModelGroup() in ['AVATTO']) {
                        device.updateSetting("minTemp", [value: fncmd , type:"number"])
                    }
                    if (settings?.txtEnable) log.info "${device.displayName} Min temperature limit is: ${fncmd} C (dp=${dp}, fncmd=${fncmd})"
                    break
                case 0x1B :    // (27) temperature calibration/correction (offset in degrees) for AVATTO, Moes and Saswell; x5hTempCorrection BEOK TRV07?
                    processTuyaCalibration( dp, fncmd )
                    break
                case 0x1D :    // (29) AVATTO
                    if (settings?.txtEnable) log.info "${device.displayName} current temperature F is: ${fncmd}"
                    break
                case 0x1E :    // (30) x5hWeeklyProcedure BEOK
                    if (settings?.txtEnable) log.info "${device.displayName} weekly procedure is: ${fncmd}"
                    break
                case 0x1F :    // (31) x5hWorkingDaySetting BEOK TRV07?
                    if (settings?.txtEnable) log.info "${device.displayName} working day setting is: ${fncmd}"
                    break
                case 0x23 :    // (35) LIDL BatteryVoltage
                    if (settings?.txtEnable) log.info "${device.displayName} BatteryVoltage is: ${fncmd}"
                    break
                case 0x24 :    // (36) : current (running) operating state (valve) AVATTO (enum) 'open','close' also MOES
                    if (settings?.txtEnable) {log.info "${device.displayName} thermostatOperatingState is: ${fncmd==0 ? 'heating' : 'idle'}"}
                    else if (settings?.logEnable) {log.info "${device.displayName} thermostatOperatingState is: ${fncmd==0 ? 'heating' : 'idle'} (dp=${dp}, fncmd=${fncmd})"}
                    sendThermostatOperatingStateEvent(fncmd==0 ? "heating" : "idle")
                    break
                case  0x27 :    // (39) AVATTO - RESET; x5hFactoryReset BEOK
                    if (fncmd==0) {
                        if (settings?.txtEnable) log.info "${device.displayName} thermostat reset state is (${fncmd})"
                    }
                    else {
                        log.warn "${device.displayName} thermostat reset state is (${fncmd}) !!!"
                    }    
                    break
                case 0x1E :    // (30) DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_3 0x1E // For Moes device
                case 0x28 :    // (40) Child Lock    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_2 0x28 ( AVATTO (boolean) ); x5hChildLock BEOK (TODO - check if boolean or enum for BEOK?)
                    if (settings?.txtEnable) log.info "${device.displayName} Child Lock is: ${fncmd} (dp=${dp})"
                    sendEvent(name: "childLock", value: (fncmd == 0) ? "off" : "on" )
                    break
                case 0x2B :    // (43) AVATTO Sensor 0-In 1-Out 2-Both; x5hSensorSelection BEOK
                    logInfo "Sensor is: ${sensorOptions[fncmd.toString()]} (${fncmd})"
                    // TODO - make it a preference parameter !
                    sendEvent(name: "sensorSelection", value: sensorOptions[fncmd.toString()])
                    break
                case 0x2C :                                             // temperature calibration (offset in degree)   //DP_IDENTIFIER_THERMOSTAT_CALIBRATION_2 0x2C // Calibration offset used by others
                    processTuyaCalibration( dp, fncmd )                 // including 'HY369'- temperature calibration (decidegree)
                    break
                case 0x2D :    // (45) LIDL and AVATTO ErrorStatus (bitmap) e1, e2, e3 // er1: Built-in sensor disconnected or fault with it; Er1: Built-in sensor disconnected or fault with it.; x5hFaultAlarm BEOK
                    if (settings?.txtEnable) log.info "${device.displayName} fault alarm error code is: ${fncmd}"
                    break
                // case 0x62 : // (98) DP_IDENTIFIER_REPORTING_TIME 0x62 (Sensors)
                case 0x65 :    // (101) AVATTO PID ; also LIDL ComfortTemp; x5hTempDiff BEOK
                    if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} Thermostat PID regulation point is: ${fncmd}"    // Model#1 only !!
                    }
                    else if (isBEOK() || isTRV07()) {       // TODO - check TRV07 - Room sensor calibration !!!!!!!!!!!
                        double floatDif = (fncmd as double) / 10.0
                        device.updateSetting("hysteresis", [value:floatDif, type:"decimal"])
                        if (settings?.txtEnable) log.info "${device.displayName} (0x65) temperature difference (hysteresis) is: ${floatDif} C (${fncmd})"
                    }
                    else {
                        if (settings?.logEnable) log.info "${device.displayName} Thermostat SCHEDULE_1 (0x65) data received (not processed)...  ${fncmd}"
                    }
                    break
                case 0x66 :     // (102) min temperature limit; also LIDL EcoTemp; x5hProtectionTempLimit BEOK (default 35)
                    if (isBEOK()) {    //  aka 'temperature ceiling'; aka protection temperature limit
                        if (settings?.txtEnable) log.info "${device.displayName} BEOK 'temperature ceiling' is: ${fncmd} C (dp=${dp}, fncmd=${fncmd})"
                        device.updateSetting("tempCeiling", [value: fncmd as int , type:"number"])    // whole number
                    }
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 Min temperature limit (dp=${dp}) is: ${fncmd/10.0} (raw=${fncmd})"
                        device.updateSetting("minTemp", [value: (fncmd/10) as int , type:"number"])

                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} Min temperature limit (UNPROCESSED) is: ${fncmd}"
                        // TODO - set minTemp for AVATTO and MOES ???
                    }
                    break
                case 0x67 :    // (103) max temperature limit; also LIDL AwaySetting; x5hOutputReverse BEOK
                    if (getModelGroup() in ['BRT-100']) {                      // #0x0267 # Boost heating countdown in second (Received value [0, 0, 1, 44] for 300)
                        if (settings?.txtEnable) log.info "${device.displayName} Boost heating countdown: ${fncmd} seconds"
                    }
                    else if (getModelGroup() in ['AVATTO']) {     // Antifreeze mode ?
                        if (settings?.txtEnable) log.info "${device.displayName} Antifreeze mode is ${fncmd==0?'off':'on'} (${fncmd})"
                    }
                    else if (isBEOK()) {
                        if (settings?.txtEnable) log.info "${device.displayName} output reverse is ${fncmd==0?'off':'on'} (${fncmd})"
                    }
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 Max temperature limit (dp=${dp}) is: ${fncmd/10.0} (raw=${fncmd})"
                        device.updateSetting("maxTemp", [value: (fncmd/10) as int , type:"number"])
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} unknown parameter is: ${fncmd} (dp=${dp}, fncmd=${fncmd}, data=${descMap?.data})"
                    }
                    // KK TODO - could be setpoint for some devices ?
                    // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT_2 0x67 // Heatsetpoint for Moe ?
                    break
                case 0x68 :     // (104) DP_IDENTIFIER_THERMOSTAT_VALVE_2 0x68 // Valve; also LIDL TempCalibration!; x5hBackplaneBrightness BEOK
                    if (getModelGroup() in ['AVATTO']) {
                        def value = safeToInt(fncmd)
                        if (settings?.txtEnable) log.info "${device.displayName} AVATTO Program Mode (104) received is: ${programModeOptions[fncmd.toString()]} (${fncmd})"      // AVATTO programm mode 0:0ff 1:Mon-Fri 2:Mon-Sat 3:Mon-Sun    
                        device.updateSetting( "programMode",  [value:value.toString(), type:"enum"] )
                    }
                    else if (isBEOK()) {
                        if (settings?.txtEnable) log.info "${device.displayName} backplane brightness is ${brightnessOptions[fncmd.toString()]} (${fncmd})"
                        device.updateSetting( "brightness",  [value: fncmd.toString(), type:"enum"] )
                        Map lastRxMap = stringToJsonMap( state.lastRx )
                        lastRxMap.setBrightness = brightnessOptions[fncmd.toString()]
                        state.lastRx   = mapToJsonString( lastRxMap)
                        sendEvent(name: "brightness", value: brightnessOptions[fncmd.toString()])
                    }
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 MOES_WINDOW_DETECT_ATTR (dp=${dp}) is: ${fncmd}" // [0,35,5] on/off, temperature, operating time (min)
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} Valve position is: ${fncmd}% (dp=${dp}, fncmd=${fncmd})"
                        // # 0x0268 # TODO - send event! (works OK with BRT-100 (values of 25 / 50 / 75 / 100) 
                    }
                    // TODO 'HY369'- unknown?
                    break
                case 0x69 :     // (105) BRT-100 temp calibration // could be also Heatsetpoint for TRV_MOE mode auto ? also LIDL
                    if (getModelGroup() in ['BRT-100']) {
                        processTuyaCalibration( dp, fncmd )
                    }
                    else if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} AVATTO unknown parameter (105) is: ${fncmd}"      // TODO: check AVATTO usage                                                 
                    }
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 BOOST mode operating time (dp=${dp}) is: ${fncmd} seconds"
                    }
                    else {
                         log.warn "${device.displayName} (DP=0x69) ?TRV_MOES auto mode Heatsetpoint? value is: ${fncmd}"
                    }
                    break
                case 0x6A :     // (106) DP_IDENTIFIER_THERMOSTAT_MODE_1 0x6A // mode used with DP_TYPE_ENUM    Energy saving mode (Received value 0:off / 1:on)
                    if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} Dead Zone temp (hysteresis) is: ${fncmd}C (dp=${dp}, fncmd=${fncmd})"
                        device.updateSetting("hysteresis", [value:fncmd, type:"decimal"])
                    }
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 MOES_FORCE_VALVE_ATTR (dp=${dp}) is: ${fncmd}"   // [0] normal [1] open [2] close
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} Energy saving mode? (dp=${dp}) is: ${fncmd} data = ${descMap?.data})"    // 0:function disabled / 1:function enabled
                    }
                    break
                case 0x6B :    // (107) DP_IDENTIFIER_TEMPERATURE 0x6B (Sensors)      // BRT-100 !
                    if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} AVATTO unknown parameter (105) is: ${fncmd}"      // TODO: check AVATTO usage                                                 
                    }
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 comfort mode temperaure (dp=${dp}) is: ${fncmd/10.0} (raw:${fncmd})"   // (decidegree)
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6B) Energy saving mode temperature value is: ${fncmd}"    // for BRT-100 # 0x026b # Energy saving mode temperature ( Received value [0, 0, 0, 15] )
                    }
                    break
                case 0x6C :    // (108)                                             
                    if (getModelGroup() in ['BRT-100']) {  
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6C) Max target temp is: ${fncmd}"        // BRT-100 ( Received value [0, 0, 0, 35] )
                        device.updateSetting("maxTemp", [value: fncmd , type:"number"])
                    }
                    else if (getModelGroup() in ['AVATTO']) {
                        logInfo "AVATTO unknown parameter (108) is: ${fncmd}"      // fncmd=0 TODO: check AVATTO usage                                                 
                    }
                    else if (getModelGroup() in ['TRV07']) {
                        logInfo "TRV07 Valve (108) is open: ${fncmd/10}%"
                        sendEvent(name: "valve", value: fncmd/10)                                              
                    }
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 eco mode temperature  (dp=${dp}) is: ${fncmd/10.0} (raw:${fncmd})"   // (decidegree)
                    }
                    else {
                        logInfo "(dp=108) unknown parameter value is: ${fncmd}"        // DP_IDENTIFIER_HUMIDITY 0x6C  (Sensors)
                    }
                    // KK Tuya cmd: dp=108 value=404095046 descMap.data = [00, 08, 6C, 00, 00, 18, 06, 00, 28, 08, 00, 1C, 0B, 1E, 32, 0C, 1E, 32, 11, 00, 18, 16, 00, 46, 08, 00, 50, 17, 00, 3C]
                    break
                case 0x6D :    // (109)                                                 
                    if (getModelGroup() in ['BRT-100']) {                      // 0x026d # Min target temp (Received value [0, 0, 0, 5])
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6D) Min target temp is: ${fncmd}"
                        device.updateSetting("minTemp", [value: fncmd , type:"number"])
                    }
                    else if (getModelGroup() in ['AVATTO']) {
                        logInfo "AVATTO unknown parameter (109) is: ${fncmd}"      // TODO: check AVATTO usage                                                 
                    }
                    else if (getModelGroup() in ['TRV07']) {
                        logInfo "TRV07 model (109) is: ${fncmd}"
                    }
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 opening percentage  (dp=${dp}) is: ${fncmd} %"
                    }
                    else { // TODO 'HY369'- valveposition  TODO - event!                                              // Valve position in % (also // DP_IDENTIFIER_THERMOSTAT_SCHEDULE_4 0x6D // Not finished)
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6D) valve position is: ${fncmd} (dp=${dp}, fncmd=${fncmd})"
                    }
                    // KK TODO if (valve > 3) => On !
                    break
                case 0x6E :       // (110) Low battery    DP_IDENTIFIER_BATTERY 0x6E    // including 'HY369' lowbattery 
                    if (getModelGroup() in ['TRV07']) {
                        logInfo "TRV07 Motor thrust (110) is: ${fncmd}"
                    }   
                    else {
                        logInfo "(Low) Battery warning (DP= 0x6E) is: ${fncmd}"
                    }
                    break
                case 0x6F :      // (111) 
                    if (getModelGroup() in ['TRV07']) {
                        logInfo "TRV07 Display brightness (111) is: ${fncmd}"      // TODO: map to the preference
                    }   
                    else {
                        logInfo "HY369 Week format (dp=${dp}) is: ${fncmd}"     // [0] 5 days [1] 6 days, [2] 7 days
                    }
                    break
                case 0x70 :     // (112)
                    if (getModelGroup() in ['TRV07']) {
                        logInfo "TRV07 Software version (112) is: ${fncmd}"      // fncmd=1001 TODO: map to tuyaVersion                                                 
                    }   
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 thermostat schedule workdays (dp=${dp}) is: ${fncmd}"    // DP_IDENTIFIER_THERMOSTAT_SCHEDULE_2 0x70 // work days (6)
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} reporting status state : ${descMap?.data}"
                    }
                    break
                case 0x71 :     // (113) 
                    if (getModelGroup() in ['TRV07']) {
                        logInfo "TRV07 Screen orientation (113) is: ${fncmd}"
                    }   
                    else if (getModelGroup in ['HY369']) {
                        logInfo "HY369 thermostat schedule weeked (dp=${dp}) is: ${fncmd}"  // DP_IDENTIFIER_THERMOSTAT_SCHEDULE_3 0x71 // holiday = Not working day (6)
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} reporting status state : ${descMap?.data}"
                    }
                    break
                case 0x72 :      // (114)   
                    if (getModelGroup() in ['TRV07']) {
                        logInfo "TRV07 System mode (114) is: ${fncmd}"      // TODO: check TRV07 usage as 'Sstem mode'                                           
                    }   
                    else {
                        logInfo "HY369 away mode temperature (dp=${dp}) is: ${fncmd/10.0} (raw=${fncmd})"
                    }
                    break
                case 0x73 :      // (115)   
                    if (getModelGroup() in ['TRV07']) {
                        logInfo "TRV07 Switch deviation (energy-saving mode only) (115) is: ${fncmd}"
                    }   
                    else {
                        logInfo "unknown parameter (115) is: ${fncmd}"
                    }
                    break
                case 0x74 :      // (116)   // LIDL OpenwindowTemp
                    if (getModelGroup() in ['TRV07']) {
                        logInfo "TRV07 Motor Data (116) is: ${fncmd}"
                    }   
                    else {
                        logInfo "HY369 auto lock (dp=${dp}) is: ${fncmd}"   // [0] auto [1] manual
                    }
                    break
                case 0x75 :      // (117)   // LIDL OpenwindowTemp
                    logInfo "HY369 away mode duration (dp=${dp}) is: ${fncmd} days"
                    break
                default :
                    if (settings?.logEnable) log.warn "${device.displayName} NOT PROCESSED Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                    break
            } //  (dp) switch
        }
        else if (descMap?.cluster == "0000") {
            if (settings?.logEnable) log.debug "${device.displayName} basic cluster report  : descMap = ${descMap}"
        } 
        else {
            if (settings?.logEnable) log.warn "not parsed : "+descMap
        }
    } // if catchAll || readAttr
}


def syncTuyaDateTime() {
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present.
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).                 // Y2K = 946684800 
    def offset = 0
    def offsetHours = 0
    Calendar cal=Calendar.getInstance();    //it return same time as new Date()
    def hour = cal.get(Calendar.HOUR_OF_DAY)
    try {
        offset = location.getTimeZone().getOffset(new Date().getTime()) 
        offsetHours = (offset / 3600000) as int
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h"
    } catch(e) {
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
    }
    //
    def cmds
    if (isBEOK()) {
        // do NOT synchronize the clock between 00:00 and 09:00 local time !!
        if (hour >= 8) {
            cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)((now() - 3600000L * (8-offsetHours))/1000),8) +  zigbee.convertToHexString((int)((now() + 3600000L * 8) / 1000), 8))    // works OK between 8 and 24 h
        }
        else {
            logDebug "skipped time synchronization (current hour is ${hour} h)"
            return
        }
    }
    else {
        cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
    }
    logDebug "sending time data : ${cmds}"
    cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
    incTxCtr()
    setLastRx( NOT_SET, NOT_SET)    // -1
}


def processTuyaHeatSetpointReport( fncmd )
{                        
    double setpointValue
    def model = getModelGroup()
    if (getModelGroup() in ['AVATTO', 'MOES', 'BRT-100' ]) {
        setpointValue = fncmd as int
    }
    else if (getModelGroup() in ['BEOK', 'HY369', 'TRV07','TEST3']) {   // added HYS369 10/29/2023 -  current room temp (decidegree)
            setpointValue = fncmd / 10.0
    }
    else {
        setpointValue = fncmd
    }
    setpointValue = setpointValue.round(1)
    if (settings?.txtEnable) log.info "${device.displayName} heatingSetpoint is: ${setpointValue}"+"\u00B0"+"C"
    sendEvent(name: "heatingSetpoint", value: setpointValue, unit: "\u00B0"+"C")
    sendEvent(name: "thermostatSetpoint", value: setpointValue, unit: "\u00B0"+"C")        // Google Home compatibility
    //
    Map lastRxMap = stringToJsonMap( state.lastRx )
    lastRxMap.setPoint = setpointValue
    state.lastRx   = mapToJsonString( lastRxMap)      // state.lastRx
}                        

def processTuyaTemperatureReport( fncmd )
{
    double currentTemperatureValue
    def model = getModelGroup()
    switch (model) {
        case 'AVATTO' :
            currentTemperatureValue = fncmd
            break
        case 'MOES' :
        case 'BEOK' :    // confirmed to be OK!
            currentTemperatureValue = fncmd / 10.0     
            break
        case 'BRT-100' :
        case 'HY369' :
        case 'TEST3' :
        case 'TRV07' :   // added TRV07 11/14/2023 -  current room temp (decidegree)
            currentTemperatureValue = fncmd / 10.0
            break
        default :
            currentTemperatureValue = fncmd
            break
    }    
    if (currentTemperatureValue > 50 || currentTemperatureValue < 1) {
        log.warn "${device.displayName} invalid temperature : ${currentTemperatureValue}"
        // auto correct patch!
        currentTemperatureValue = currentTemperatureValue / 10.0
        log.warn "auto correct patch for temperature!"
    }
    if (settings?.txtEnable) log.info "${device.displayName} temperature is: ${currentTemperatureValue}"+"\u00B0"+"C"
    sendEvent(name: "temperature", value: currentTemperatureValue, unit: "\u00B0"+"C")
}

def processTuyaCalibration( dp, fncmd )
{
    def temp = fncmd 
    double doubleCalib = temp
    if (getModelGroup() in ['AVATTO'] ){    // (dp=27, fncmd number)
        device.updateSetting("tempCalibration", [value: temp , type:"decimal"])
        //logDebug "AVATTO calibration"
    }
    else if (isBEOK()){    // (dp=27, fncmd decimal X.X)
        doubleCalib = safeToDouble(fncmd) / 10.0
        device.updateSetting("tempCalibration", [value:doubleCalib, type:"decimal"])
        logDebug "BEOK calibration received is: ${doubleCalib}C (${fncmd})"
    }
    else  if (getModelGroup() in ['BRT-100'] && dp == 105) { // 0x69
        device.updateSetting("tempCalibration", [value: temp , type:"decimal"])
        logDebug "BRT-100 calibration is: ${temp}"
    }
    else  if (getModelGroup() in ['HY369'] ) { // 0x2C
        device.updateSetting("tempCalibration", [value: temp / 10.0 , type:"decimal"])
        logDebug "HY369 (dp=0x2C) calibration is: ${temp/10.0}"
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} UNSUPPORTED temperature calibration for modelGroup=${getModelGroup()} : ${temp} (dp=${dp}, fncmd=${fncmd}) "
    }
/*    
    else {    // "_TZE200_aoclfnxz"
        logDebug "other calibration, getModelGroup() = ${getModelGroup()} dp=${dp} fncmd = ${fncmd}"
        if (temp > 2048) {
            temp = temp - 4096;
        }
        temp = temp / 100        // KK - check !
    }
*/    
    if (settings?.txtEnable) log.info "${device.displayName} temperature calibration (correction) is: ${doubleCalib} (dp=${dp}, fncmd=${fncmd}) "
}

def processBRT100Presets( dp, data ) {
    logDebug "processBRT100Presets fp-${dp} data=${data}"
    // 0x0401 # Mode (Received value 0:Manual / 1:Holiday / 2:Temporary Manual Mode / 3:Prog)
    // KK TODO - check why the difference for values 0 and 3 ?
/*
0x0401 :
0 : Manual Mode
1 : Holiday Mode
2 : Temporary Manual Mode (will return to Schedule mode at the next schedule time)
3 : Schedule Programming Mode


TRV sends those values when changing modes:
1 for Manual
0 for Schedule
2 for Temporary manual (in Schedule)
3 for Away

Schedule -> [0] for attribute 0x0401
Manual -> [1] for attribute 0x0401
Temp Manual -> [2] for attribute 0x0401
Holiday -> [3] for attribute 0x0401

*/
    
    def mode
    def preset
    
    if (data == 0) { //programming (schedule )
        mode = "auto"
        preset = "auto"
    }
    else if (data == 1) { //manual
        mode = "heat"
        preset = "manual"
    }
    else if (data == 2) { //temporary_manual
        mode = "heat"
        preset = "manual"
    }
    //temporary_manual
    else if (data == 3) { //holiday
        mode = "off"    // BRT-100 'holiday' preset is matched to 'off' mode!
        preset = "holiday"
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} processBRT100Presets unknown: ${data}"
        return;
    }
    if (state.lastThermostatMode == "emergency heat") {
        runIn(2, sendTuyaBoostModeOff)    // also turn boost off!
    }

    sendEvent(name: "thermostatMode", value: mode)    // mode was confirmed from the Preset info data...
    state.lastThermostatMode = mode
    
    // TODO - change tehrmostatPreset depending on preset ?
    if (settings?.txtEnable) log.info "${device.displayName} BRT-100 Presets: mode = ${mode} preset = ${preset}"
}

def processTuyaModes3( dp, data ) {
    // dp = 0x0402 : // preset for moes or mode
    // dp = 0x0403 : // preset for moes    
    if (getModelGroup() in ['BRT-100', 'HY369']) {    // BRT-100 ?    KK: TODO!
        def mode
        if (data == 0) { mode = "auto" } //schedule
        else if (data == 1) { mode = "heat" } //manual
        else if (data == 2) { mode = "off" } //away
        else {
            if (settings?.logEnable) log.warn "${device.displayName} processTuyaModes3: unknown mode: ${data}"
            return
        }
        if (settings?.txtEnable) log.info "${device.displayName} mode is: ${mode}"
        // TODO - - change thremostatMode depending on mode ?
    }
    else {    // NOT TESTED !!
        def preset
        if (dp == 0x02) { // 0x0402
            preset = "auto" 
        }
        else if (dp == 0x03) { // 0x0403
            preset = "program"
        }
        else {
            if (settings?.logEnable) log.warn "${device.displayName} processTuyaMode3: unknown preset: dp=${dp} data=${data}"
            return
        }
        if (settings?.txtEnable) log.info "${device.displayName} preset is: ${preset}"
        // TODO - - change preset ?
    }
}

def processTuyaBoostModeReport( fncmd )
{
    def boostMode = fncmd == 0 ? "off" : "on"                // 0:"off" : 1:"boost in progress"
    if (settings?.txtEnable) log.info "${device.displayName} Boost mode is: $boostMode (0x${fncmd})"
    if (boostMode == "on") {
        sendEvent(name: "thermostatMode", value: "emergency heat")
        state.lastThermostatMode = "emergency heat"
        sendThermostatOperatingStateEvent("heating")
    }
    else {
        if (device.currentState('thermostatMode').value == "emergency heat") {
            // restore the lastThermostatMode
            if (settings?.txtEnable) log.info "${device.displayName} restoring the lastThermostatMode: ${state.lastThermostatMode}"
            setThermostatMode(state.lastThermostatMode)
        }
        else {
            if (settings?.logEnable) log.debug "boost is already off - last thermostatMode was ${device.currentState('thermostatMode').value}"
        }
    }
}


private int getTuyaAttributeValue(ArrayList _data) {
    int retValue = 0
    
    if (_data.size() >= 6) {
        int dataLength = _data[5] as Integer
        int power = 1;
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[i+5])
            power = power * 256
        }
    }
    return retValue
}

def sendThermostatOperatingStateEvent( st ) {
    sendEvent(name: "thermostatOperatingState", value: st)
    state.lastThermostatOperatingState = st
}

def guessThermostatOperatingState() {
    try {
        double dTemp = Double.parseDouble(device.currentState('temperature').value)
        double dSet  = Double.parseDouble(device.currentState('heatingSetpoint').value)
 
        if (dTemp >= dSet) {
            if (settings?.txtEnable) log.debug "guessing operating state is IDLE"
            return "idle"
        }
        else {
            if (settings?.txtEnable) log.debug "guessing operating state is HEAT"
            return "heat"
        }
    }
    catch (NumberFormatException e) {
        return "unknown"
    }
}

def sendTuyaBoostModeOff() {
    ArrayList<String> cmds = []
    if (settings?.txtEnable) log.info "${device.displayName} turning Boost mode off"
    sendThermostatOperatingStateEvent( guessThermostatOperatingState() )
    //sendEvent(name: "thermostatOperatingState", value: guessThermostatOperatingState())
    cmds = sendTuyaCommand("04", DP_TYPE_BOOL, "00")
    sendZigbeeCommands( cmds )    
}

// called from setThermostatMode( mode ) only
// TODO - refactor (switch by model) !
def sendTuyaThermostatMode( mode ) {
    ArrayList<String> cmds = []
    def dp = ""
    def fn = ""   
    def model = getModelGroup()
    switch (mode) {
        case "off" :    // 
            if (model in ['AVATTO', 'MOES', 'BEOK']) {
                dp = "01"
                fn = "00"
            }
            else if (model in ['BRT-100']) {    // BRT-100: off mode is also reffered as 'holiday' or 'away'
                dp = "01"
                fn = "03"
                if (state.lastThermostatMode == "emergency heat") {
                    runIn(2, sendTuyaBoostModeOff)    // also turn boost off after 2 seconds!
                }
                return sendTuyaCommand(dp, DP_TYPE_ENUM, fn)    // BRT-100 DP=1 needs DP_TYPE_ENUM!                
            }
            else if (model in ['TRV07']) {
                dp = "01"
                fn = "02"
            }
            else {    // all other models
                dp = "01"                            
                fn = "00"    // changed 10/23/2022 defauilt to DP1 FN=0 
            }
            break
        case "heat" :    // manual mode
            if (model in ['AVATTO', 'MOES', 'BEOK']) {    // TODO - this command only does not switch off Scheduled (auto) mode !
                if (device.currentState('thermostatMode').value == "off") {
                    cmds += switchThermostatOn()
                }
                dp = "02"    // was "01" 
                fn = "00"    // was "01" 
            }
            else if (model in ['BRT-100']) {
                dp = "01"
                fn = "01"
                return sendTuyaCommand(dp, DP_TYPE_ENUM, fn)    // BRT-100 DP=1 needs DP_TYPE_ENUM!
            }
            else if (model in ["TRV07"]) {
                dp = "01"
                fn = "01"
            }
            else {    // all other models    // not tested!
                dp = "02"                            
                fn = "00"    // changed 10/23/2022 defauilt to DP2 FN=0 
            }
            break
        case "auto" :    // scheduled mode
            logDebug "sending AUTO mode!"
            if (model in ['AVATTO', 'MOES', 'BEOK']) {    // TODO - does not switch off manual mode ?
                if (device.currentState('thermostatMode').value == "off") {
                    cmds += switchThermostatOn()
                }
                dp = "02"
                fn = "01"
            }
            else if (model in ['BRT-100']) {
                dp = "01"                       
                fn = "00"  
                return sendTuyaCommand(dp, DP_TYPE_ENUM, fn)    // BRT-100 DP=1 needs DP_TYPE_ENUM!
            }
            else if (model in ["TRV07"]) {
                dp = "01"
                fn = "00"
            }
            else {    // all other models    // not tested!
                dp = "02"                            
                fn = "01"    // changed 10/23/2022 defauilt to DP2 FN=1 
            }
            break
        case "emergency heat" :
            if (model in ['BRT-100']) {    // BRT-100
                //state.mode = "emergency heat"
                dp = "04"                            
                fn = "01"
            }
            else {    // all other models do not support "emergency heat" !
                if (settings?.txtEnable) log.warn "${device.displayName} 'emergency heat' mode is not supported by this device"
                return null
            }       
            break
        case "cool" :
            if (settings?.txtEnable) log.warn "${device.displayName} 'cool' mode is not supported by this device"
            return null
            break
        case "on" :
            if (model in ["TRV07"]) {
                dp = "01"
                fn = "03"
            }
            else {    // all other models do not support "on" ! Is this actually true???
                if (settings?.txtEnable) log.warn "${device.displayName} 'on' mode is not supported by this device"
                return null
            }      
        default :
            log.warn "Unsupported mode ${mode}"
            return null
    }
    //wakeUpTuya()
    cmds += sendTuyaCommand(dp, mode == "off" ? DP_TYPE_BOOL : DP_TYPE_ENUM, fn)
    sendZigbeeCommands( cmds )
}

// called from heat() off() auto() ...
// sends TuyaCommand and checks after 4 seconds
def setThermostatMode( mode ) {
    if (settings?.logEnable) log.debug "${device.displayName} sending setThermostatMode(${mode})"
    setLastTx( mode=mode, isModeSetReq=true)
    //state.mode = mode
    runIn(4, modeReceiveCheck)
    sendTuyaThermostatMode( mode )
}



def sendTuyaHeatingSetpoint( temperature ) {
    if (settings?.logEnable) log.debug "${device.displayName} sendTuyaHeatingSetpoint(${temperature})"
    def settemp = temperature as int
    def dp = "10"
    def model = getModelGroup()
    switch (model) {
        case 'AVATTO' :                          // AVATTO - only integer setPoints!
            dp = "10"
            settemp = temperature
            break
        case 'MOES' :                            // MOES - 0.5 precision? ( and double the setpoint value ? ) TODO !!!
            dp = "10"
            settemp = temperature                // KK check!
            break
        case 'BEOK' :                            // 
            dp = "10"
            settemp = temperature * 10               
            break
        case 'BRT-100' :                         // BRT-100
            dp = "02"                            
            settemp = temperature
            break
        case 'TRV07' :                           // added 11/14/2023
            dp = "02"                            
            settemp = temperature * 10
            break
        case 'HY369' :
            dp = "02"
            settemp = temperature * 10      // 10/29/2023 
            break
        case 'TEST3' :
            //dp = "02"
            settemp = temperature
            break
        default :
            settemp = temperature
            break
    }    
    if (settings?.logEnable) log.debug "${device.displayName} changing setpoint to ${settemp}"
    //
    Map lastTxMap = stringToJsonMap( state.lastTx )
    lastTxMap.isSetPointReq = true
    lastTxMap.setPoint = temperature    // BEOK - float value!
    state.lastTx = mapToJsonString( lastTxMap )    // save everything back to state.lastTx
    //
    runIn(3, setpointReceiveCheck)
    sendZigbeeCommands( sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(settemp as int, 8)) )
}

def setThermostatSetpoint( temperature ) {
    setHeatingSetpoint( temperature )
}



//  ThermostatHeatingSetpoint command
//  sends TuyaCommand and checks after 4 seconds
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface)
def setHeatingSetpoint( temperature ) {
    def previousSetpoint = device.currentState('heatingSetpoint', true).value /*as int*/
    double tempDouble
    logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})"
    if (isBEOK() || isTRV07()) {
        if (settings?.logEnable) log.debug "0.5 C correction of the heating setpoint${temperature} for BEOK"
        tempDouble = safeToDouble(temperature)
        tempDouble = Math.round(tempDouble * 2) / 2.0
    }
    else {
        if (temperature != (temperature as int)) {
            if ((temperature as double) > (previousSetpoint as double)) {
                temperature = (temperature + 0.5 ) as int
            }
            else {
                temperature = temperature as int
            }
        logDebug "corrected heating setpoint${temperature}"
        }
        tempDouble = temperature
    }
    if (settings?.maxTemp == null || settings?.minTemp == null ) { device.updateSetting("minTemp", [value: 5 , type:"number"]);  device.updateSetting("maxTemp", [value: 35 , type:"number"])   }
    if (tempDouble > settings?.maxTemp.value ) tempDouble = settings?.maxTemp.value
    if (tempDouble < settings?.minTemp.value ) tempDouble = settings?.minTemp.value
    tempDouble = tempDouble.round(1)
    sendEvent(name: "heatingSetpoint", value: tempDouble, unit: "\u00B0"+"C")
    sendEvent(name: "thermostatSetpoint", value: tempDouble, unit: "\u00B0"+"C")
    updateDataValue("lastRunningMode", "heat")
    
    state.heatingSetPointRetry = 0
    sendTuyaHeatingSetpoint( tempDouble )
}


def setCoolingSetpoint(temperature){
    if (settings?.logEnable) log.debug "${device.displayName} setCoolingSetpoint(${temperature}) called!"
    if (temperature != (temperature as int)) {
        temperature = (temperature + 0.5 ) as int
        logDebug "corrected temperature: ${temperature}"
    }
    sendEvent(name: "coolingSetpoint", value: temperature, unit: "\u00B0"+"C")
}

def heat(){
    setThermostatMode("heat")
}

def off(){
    setThermostatMode("off")
}

def on() {
    heat()
}

def setThermostatFanMode(fanMode) { sendEvent(name: "thermostatFanMode", value: "${fanMode}", descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}")) }

def auto() { setThermostatMode("auto") }
def emergencyHeat() { setThermostatMode("emergency heat") }
def cool() { setThermostatMode("cool") }
def fanAuto() { setThermostatFanMode("auto") }
def fanCirculate() { setThermostatFanMode("circulate") }
def fanOn() { setThermostatFanMode("on") }
def setSchedule(schedule) { if (settings?.logEnable) log.debug "${device.displayName} setSchedule (${schedule}) called!"} 

def setManualMode() {
    if (settings?.logEnable) log.debug "${device.displayName} setManualMode()"
    ArrayList<String> cmds = []
    cmds = sendTuyaCommand("02", DP_TYPE_ENUM, "00") + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code  // TODO: check!
    sendZigbeeCommands( cmds )
}

def switchThermostatOn() {
    if (settings?.logEnable) log.debug "${device.displayName} switching On!"
    ArrayList<String> cmds = []
    cmds = sendTuyaCommand("01", DP_TYPE_BOOL, "01", delay=2750)    // increased delay to 2750 on 1/27/2022
    return cmds
}


/**
 * Returns the model group of the device based on its manufacturer.
 * If modelGroupPreference is set to "Auto detect", it will try to match the manufacturer with a known model group.
 * If no match is found, it will return "UNKNOWN".
 * If modelGroupPreference is set to a specific model group, it will return that model group.
 * @return The model group of the device.
 */
def getModelGroup() {
    def manufacturer = device.getDataValue("manufacturer")
    def modelGroup = 'UNKNOWN'
    if (modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference", "Auto detect")
    }
    if (modelGroupPreference == "Auto detect") {
        if (manufacturer in Models) {
            modelGroup = Models[manufacturer]
        }
        else {
             modelGroup = 'UNKNOWN'
        }
    }
    else {
         modelGroup = modelGroupPreference 
    }
    //logDebug "${device.displayName} manufacturer ${manufacturer} group is ${modelGroup}"
    return modelGroup
}



def sendSupportedThermostatModes() {
    def supportedThermostatModes = []
    switch (getModelGroup()) {
        case 'AVATTO' :
        case 'MOES' :
        case 'BEOK' :
            supportedThermostatModes = ["off", "heat", "auto"]
            break
        case 'BRT-100' :  // BRT-100
            supportedThermostatModes = ["off", "heat", "auto", "emergency heat"]
            break
        default :
            supportedThermostatModes = ["off", "heat", "auto"]
            break
    }    
    sendEvent(name: "supportedThermostatModes", value:  JsonOutput.toJson(supportedThermostatModes), isStateChange: true)
}

//  called from initialize()
def installed() {
    if (settings?.txtEnable) log.info "installed()"
    
    sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(["auto", "circulate", "on"]), isStateChange: true)    
    sendSupportedThermostatModes()
    sendEvent(name: "thermostatMode", value: "heat", isStateChange: true)
    sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: true)
    state.lastThermostatMode = "heat"
    sendThermostatOperatingStateEvent( "idle" )
    sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true)
    sendEvent(name: "thermostatSetpoint", value:  20.0, unit: "\u00B0"+"C", isStateChange: true)        // Google Home compatibility
    sendEvent(name: "heatingSetpoint", value: 20.0, unit: "\u00B0"+"C", isStateChange: true)
    sendEvent(name: "coolingSetpoint", value: 30.0, unit: "\u00B0"+"C", isStateChange: true)
    sendEvent(name: "temperature", value: 22.0, unit: "\u00B0"+"C", isStateChange: true)    
    updateDataValue("lastRunningMode", "heat")    

    //state.setpoint = 0
    unschedule()
    runEvery1Minute(receiveCheck)    // KK: check
}

def updated() {
    ArrayList<String> cmds = []
    if (settings?.modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference", [value:"Auto detect", type:"enum"])
        state.deviceProfile = "AUTO_DETECT"
    }

    // log.warn "settings?.modelGroupPreference = ${settings?.modelGroupPreference}"
    if (settings?.modelGroupPreference != null) {
        logDebug "current state.deviceProfile=${state.deviceProfile}, settings.modelGroupPreference=${settings?.modelGroupPreference}, getModelGroup()=${getModelGroup()}"
        if (settings?.modelGroupPreference != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${settings?.modelGroupPreference}"
            state.deviceProfile = settings?.modelGroupPreference
            initializeVars(fullInit = false) 
            //resetPreferencesToDefaults(debug=true)
            logInfo "press F5 to refresh the page"
        }
    }
    else {
        logDebug "modelGroupPreference is not set"
    }

    /* unconditional */log.info "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b> modelGroupPreference = <b>${modelGroupPreference}</b> (${getModelGroup()})"
    if (settings?.txtEnable) log.info "Force manual is <b>${forceManual}</b>; Resend failed is <b>${resendFailed}</b>"
    if (settings?.txtEnable) log.info "Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (!(getModelGroup() in ['BRT-100'])) {
        if (settings?.homeKitCompatibility?.value  == true && device.currentValue("battery", true) == null) {
            sendEvent(name: 'battery', value: 100, unit: "%", type: "digital", descriptionText: "homeKitCompatibility on", isStateChange: true )    
        }
        else if (settings?.homeKitCompatibility?.value  == false && device.currentValue("battery", true) != null) {
            sendEvent(name: 'homeKitCompatibility', value: "off", unit: "%", type: "digital", descriptionText: "homeKitCompatibility off", isStateChange: true )    // for the record
            device.deleteCurrentState("battery")
        }
    }
    if (logEnable==true) {
        runIn(86400, logsOff, [overwrite: true, misfire: "ignore"])    // turn off debug logging after 24 hours
    }
    else {
        unschedule(logsOff)
    }
    runIn( defaultPollingInterval, deviceHealthCheck, [overwrite: true, misfire: "ignore"])
    def fncmd
    def dp
    // tempCalibration
    dp = getModelGroup() in ['AVATTO', 'BEOK'] ? "1B" : getModelGroup() in ['BRT-100'] ? "69" : null
    if (getModelGroup() in ['AVATTO', 'BEOK', 'BRT-100'] && dp != null) {
        logDebug "tempCalibration = ${tempCalibration}"
        fncmd = getModelGroup() in [ 'BEOK'] ? (safeToDouble( tempCalibration )*10) as int : safeToDouble( tempCalibration ) as int
        logDebug "tempCalibration fncmd = ${fncmd}"
        logDebug "setting tempCalibration to ${tempCalibration} (${fncmd})"
        cmds += sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))  
    }
    // hysteresis
    dp = getModelGroup() in ['AVATTO'] ? "6A" : getModelGroup() in ['BEOK'] ? "65" : null
    if (getModelGroup() in ['AVATTO', 'BEOK']) {
        fncmd = getModelGroup() in [ 'BEOK'] ? (safeToDouble( hysteresis )*10) as int : safeToInt( hysteresis ) 
        logDebug "setting hysteresis to ${hysteresis} (${fncmd})"
        cmds += sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
    }
    // minTemp
    dp = getModelGroup() in ['AVATTO'] ? "1A" : getModelGroup() in ['BRT-100'] ? "6D" : null
    if (getModelGroup() in ['AVATTO','BRT-100'] && dp != null) {    // no min temp for BEOK!
        fncmd = safeToInt( minTemp )
        logDebug "setting minTemp to ${fncmd}"
        cmds += sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))     
    }
    // maxTemp
    dp = getModelGroup() in ['AVATTO', 'BEOK'] ? "13" : getModelGroup() in ['BRT-100'] ? "6C" : null
    if (dp != null && getModelGroup() in ['AVATTO','BRT-100']) {
        fncmd = safeToInt( maxTemp )
        if (settings?.logEnable) log.trace "${device.displayName} setting maxTemp to= ${fncmd}"
        cmds += sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
    }
    else if (dp != null && getModelGroup() in ['BEOK']) {
        fncmd = safeToInt( maxTemp )
        if (fncmd > 60 ) fncmd = 60
        fncmd = fncmd * 10
        if (settings?.logEnable) log.trace "${device.displayName} SKIPPING sending BEOK maxTemp  ${fncmd/10} (${fncmd})"
        // changes the heating set point !!!
    }
    // tempCeiling
    dp = getModelGroup() in ['BEOK'] ? "66" : null
    if (getModelGroup() in ['BEOK'] && dp != null) {    // TODO: tempCeiling for AVATTO and MOES !
        fncmd = safeToInt( tempCeiling ) // whole number!
        if (settings?.logEnable) log.trace "${device.displayName} setting tempCeiling to ${tempCeiling} (${fncmd})"
        cmds += sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
    }
    // programMode
    if (getModelGroup() in ['AVATTO']) {
        if (settings?.programMode != null) {
            def value = safeToInt( programMode )
            if (settings?.logEnable) log.debug "${device.displayName} setting Program Mode to ${programModeOptions[value.toString()]} (${programMode})"
            cmds += sendTuyaCommand("68", DP_TYPE_ENUM, zigbee.convertToHexString(value as int, 2))
        }
    }
    // sound
    if (isBEOK()) {
        fncmd = settings?.sound == false ? 0 : 1
        if (settings?.logEnable) log.trace "${device.displayName} setting sound to ${fncmd} (${fncmd==0?'off':'on'})"
        cmds += sendTuyaCommand("07", DP_TYPE_BOOL, zigbee.convertToHexString(fncmd as int, 2))
    }
    // frostProtection
    if (isBEOK()) {
        fncmd = settings?.frostProtection == false ? 0 : 1
        if (settings?.logEnable) log.trace "${device.displayName} setting frost protection to ${fncmd} (${fncmd==0?'off':'on'})"
        cmds += sendTuyaCommand("0A", DP_TYPE_BOOL, zigbee.convertToHexString(fncmd as int, 2))
    }
    // brightness
    if (isBEOK()) {
        if (settings?.logEnable) log.trace "settings?.brightness = ${settings?.brightness}"
        if (settings?.brightness != null) {
            def key = safeToInt(settings?.brightness)
            def value = brightnessOptions[key.toString()]
            //log.trace "key=${key} value=${value}"
            if (value != null) {
                def dpValHex = zigbee.convertToHexString(key as int, 2)
                cmds += sendTuyaCommand("68", DP_TYPE_ENUM, dpValHex)            
                if (settings?.logEnable) log.debug "${device.displayName} setting brightness to ${value} ($key)"
            }
        }
    }
    
    if (cmds.size() > 3) {
        logInfo "Update finished"
        logDebug "cmds = ${cmds}"
        sendZigbeeCommands( cmds )
    }
    else {
        if (settings?.logEnable) log.debug "${device.displayName} nothing to update"
    }
    //
    if (isBEOK()) {
        syncTuyaDateTime()
    }
}

def refresh() {
    ArrayList<String> cmds = []
    def model = getModelGroup()
    def fncmd
    if (settings?.logEnable)  {log.debug "${device.displayName} refresh() ${model}..."}
    switch (model) {
        /*
        case 'BRT-100' :
            def dp = "69"                            
            def fncmd = safeToInt( tempCalibration )
            cmds += sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))   
            sendZigbeeCommands( cmds ) 
        */
        case 'AVATTO' :  // wakes up AVATTO - LCD display goes to normal brightness
            fncmd = 0
            cmds += sendTuyaCommand("27", DP_TYPE_ENUM, zigbee.convertToHexString(fncmd as int, 8))
            break
        case 'BEOK' :    // does nothing 
            fncmd = 0
            cmds += sendTuyaCommand("27", DP_TYPE_ENUM, zigbee.convertToHexString(fncmd as int, 2))
            break
        default :
            cmds += zigbee.readAttribute(0 , 0 )
            break
    }
    sendZigbeeCommands( cmds ) 
}

def factoryReset( yes ) {
    ArrayList<String> cmds = []
    def model = getModelGroup()    
    if (yes != "YES") {
        log.warn "${device.displayName} type '<b>YES</b>' to confirm ${model} factory resetting!"
        return
    }

    def fncmd
    log.warn "${device.displayName} FACTORY RESET ${model}..."
    switch (model) {
        /*
        case 'BRT-100' :
            def dp = "69"                            
            def fncmd = safeToInt( tempCalibration )
            cmds += sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))   
            sendZigbeeCommands( cmds ) 
        */
        case 'AVATTO' :
        case 'BEOK' :
            fncmd = 1    // reset!
            cmds += sendTuyaCommand("27", DP_TYPE_BOOL, zigbee.convertToHexString(fncmd as int, 2))
            break
        default :
            cmds += zigbee.readAttribute(0 , 0 )
            break
    }
    sendZigbeeCommands( cmds )     
}


def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

/**
 * Checks if the driver version has changed and updates the settings if necessary.
 * If the driver version has changed, the method initializes the variables, removes deprecated state variables,
 * and resets the statistics. Finally, it updates the driver version in the state.
 */
def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        if (txtEnable==true) log.debug "${device.displayName} updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        if (device.currentValue("presence", true) != null) {
            device.deleteCurrentState("presence")                // removed from version 1.3.0
        }
        if (state.rxCounter != null) state.remove("rxCounter")
        if (state.txCounter != null) state.remove("txCounter")
        if (state.old_dp != null)    state.remove("old_dp")
        if (state.old_fncmd != null) state.remove("old_fncmd")
        if (state.setpoint != null)  state.remove("setpoint")
        if (state.modeSetRetry != null)  state.remove("modeSetRetry")
        if (state.heatingSetPointRetry != null)  state.remove("heatingSetPointRetry")
        //
        if (state.lastRx == null || state.stats == null || state.lastTx == null) {
            resetStats()
        }
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

def setPresent() { setHealthStatusOnline() }    // trap for backward compatibility versions prior 1.3.0
def pollPresence() { deviceHealthCheck() }      // trap for backward compatibility versions prior 1.3.0

// 
/**
 * Called when any event was received from the Zigbee device in parse() method..
 * Sets the health status of the device to "online" if it is not already set to "online".
 * Sends a health status event and logs that the device is now online.
 * Schedules a device health check to run after the default polling interval.
 */
def setHealthStatusOnline() {
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online']))  {
        sendHealthStatusEvent('online')
        logInfo "is now online!"
        runIn( defaultPollingInterval, deviceHealthCheck, [overwrite: true, misfire: "ignore"])
    }
    state.notPresentCounter = 0
}


/**
 * called every 60 minutes
 * Checks the health status of the device and sends an event if it is offline.
 * Also synchronizes the date and time if the device is a BEOK thermostat.
 *
 * @return none
 */
def deviceHealthCheck() {
    if (state.notPresentCounter != null) {
        state.notPresentCounter = state.notPresentCounter + 1
        if (state.notPresentCounter >= presenceCountTreshold) {
            if ((device.currentValue("healthStatus") ?: 'unknown') != 'offline' ) {
                logWarn "not present!"
                sendHealthStatusEvent('offline')
            }
        }
    }
    else {
        state.notPresentCounter = 0  
    }
    if (isBEOK())  {
        syncTuyaDateTime()
    }
    runIn( defaultPollingInterval, deviceHealthCheck, [overwrite: true, misfire: "ignore"])
}

void sendHealthStatusEvent(value) {
    def descriptionText = "healthStatus changed to ${value}"
    sendEvent(name: "healthStatus", value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true)
    if (value == 'online') {
        logInfo "${descriptionText}"
    }
    else {
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" }
    }
}


def ping() {
    logInfo 'ping...'
    scheduleCommandTimeoutCheck()
    Map lastTxMap = stringToJsonMap(state.lastTx)
    lastTxMap.pingTime = new Date().getTime()
    sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) )
    state.lastTx = mapToJsonString( lastTxMap )
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    runIn(delay, 'deviceCommandTimeout')
}

void deviceCommandTimeout() {
    logWarn 'no response received (sleepy device or offline?)'
    sendRttEvent("timeout")
}

void sendRttEvent( String value=null) {
    def now = new Date().getTime()
    Map lastTxMap = stringToJsonMap( state.lastTx )
    def timeRunning = now.toInteger() - (lastTxMap.pingTime ?: now).toInteger()
    def descriptionText = "Round-trip time is ${timeRunning} (ms)"
    if (value == null) {
        logInfo "${descriptionText}"
        sendEvent(name: "rtt", value: timeRunning, descriptionText: descriptionText, unit: "ms", isDigital: true)    
    }
    else {
        descriptionText = "Round-trip time is ${value}"
        logInfo "${descriptionText}"
        sendEvent(name: "rtt", value: value, descriptionText: descriptionText, isDigital: true)    
    }
}


def logInitializeRezults() {
    log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")} ModelGroup = ${getModelGroup()}"
    log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// called by initialize() button
void initializeVars( boolean fullInit = true ) {
    if (settings?.txtEnable) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        resetStats()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown')    
    //
    setLastRx( NOT_SET, NOT_SET)    // -1
    state.packetID = 0
    //
    if (fullInit == true || state.lastThermostatMode == null) state.lastThermostatMode = "unknown"
    if (fullInit == true || state.lastThermostatOperatingState == null) state.lastThermostatOperatingState = "unknown"
    if (fullInit == true || state.notPresentCounter == null) state.notPresentCounter = 0
    //
    if (fullInit == true || settings?.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit == true || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings?.forceManual == null) device.updateSetting("forceManual", false)    
    if (fullInit == true || settings?.resendFailed == null) device.updateSetting("resendFailed", false)    
    if (fullInit == true || settings?.minTemp == null) device.updateSetting("minTemp", [value: 10 , type:"number"])    
    if (fullInit == true || settings?.maxTemp == null) device.updateSetting("maxTemp", [value: 35 , type:"number"])
    if (fullInit == true || settings?.tempCeiling == null) device.updateSetting("tempCeiling", [value: 35 , type:"number"])
    if (fullInit == true || settings?.tempCalibration == null) device.updateSetting("tempCalibration", [value:0.0, type:"decimal"])
    if (fullInit == true || settings?.hysteresis == null) device.updateSetting("hysteresis", [value:1.0, type:"decimal"])
    if (fullInit == true || settings?.sound == null) device.updateSetting("sound", true)    
    if (fullInit == true || settings?.frostProtection == null) device.updateSetting("frostProtection", true)    
    if (fullInit == true || settings?.brightness == null) device.updateSetting("brightness", [value:"3", type:"enum"])
    if (fullInit == true || settings?.homeKitCompatibility == null) device.updateSetting("homeKitCompatibility", true)    
    
    //
}

def configure() {
    initialize()
}

def initialize() {
    if (true) "${device.displayName} Initialize()..."
    unschedule()
    initializeVars()
    runIn( defaultPollingInterval, deviceHealthCheck, [overwrite: true, misfire: "ignore"])
    setDeviceLimits()
    installed()
    updated()
    runIn( 3, logInitializeRezults)
}

def setDeviceLimits() { // for google and amazon compatability
    sendEvent(name:"minHeatingSetpoint", value: settings.minTemp ?: 10, unit: "°C", isStateChange: true)
    sendEvent(name:"maxHeatingSetpoint", value: settings.maxTemp ?: 35, unit: "°C", isStateChange: true)
    updateDataValue("lastRunningMode", "heat")
    if (settings?.logEnable) log.trace "setDeviceLimits - device max/min set"
}    

// scheduled for call from setThermostatMode() 4 seconds after the mode was potentiually changed.
// also, called every 1 minute from receiveCheck()
def modeReceiveCheck() {
    if (settings?.resendFailed == false )
        return
    Map lastTxMap = stringToJsonMap( state.lastTx )
    if (lastTxMap.isModeSetReq == false)
        return
    Map statsMap = stringToJsonMap( state.stats )
    
    if (lastTxMap.mode != device.currentState('thermostatMode', true).value) {
        lastTxMap['setModeRetries'] = lastTxMap['setModeRetries'] + 1
        logWarn "modeReceiveCheck() <b>failed</b> (expected ${lastTxMap['mode']}, current ${device.currentState('thermostatMode', true).value}), retry#${lastTxMap['setModeRetries']}"
        if (lastTxMap['setModeRetries'] < MaxRetries) {
            logDebug "resending mode command : ${lastTxMap['mode']}"
            statsMap['txFailCtr'] = statsMap['txFailCtr'] + 1
            setThermostatMode( lastTxMap['mode'] )
        }
        else {
            logWarn "modeReceiveCheck(${lastTxMap['mode'] }}) <b>giving up retrying<b/>"
            lastTxMap['isModeSetReq'] = false    // giving up
            lastTxMap['setModeRetries'] = 0
        }
    }
    else {
        logDebug "modeReceiveCheck mode was changed OK to (${lastTxMap['mode']}). No need for further checks."
        lastTxMap.isModeSetReq = false    // setting mode was successfuly confimed, no need for further checks
        lastTxMap.setModeRetries = 0
    }
    state.lastTx = mapToJsonString( lastTxMap )    // save everything back to state.lastTx
    state.stats  = mapToJsonString( statsMap)      // save everything back to state.stats
}


//
//  also, called every 1 minute from receiveCheck()
def setpointReceiveCheck() {
    if (settings?.resendFailed == false )
        return

    Map lastTxMap = stringToJsonMap( state.lastTx )
    if (lastTxMap.isSetPointReq == false)
        return
    Map statsMap = stringToJsonMap( state.stats )
    Map lastRxMap = stringToJsonMap( state.lastRx )
    
    if (lastTxMap.setPoint != NOT_SET && ((lastTxMap.setPoint as String) != (lastRxMap.setPoint as String))) {
        lastTxMap.setPointRetries = lastTxMap.setPointRetries + 1
        if (lastTxMap.setPointRetries < MaxRetries) {
            logWarn "setpointReceiveCheck(${lastTxMap.setPoint}) <b>failed<b/> (last received is still ${lastRxMap.setPoint})"
            logDebug "resending setpoint command : ${lastTxMap.setPoint} (retry# ${lastTxMap.setPointRetries})"
            statsMap.txFailCtr = statsMap.txFailCtr + 1
            sendTuyaHeatingSetpoint(lastTxMap.setPoint)
        }
        else {
            logWarn "setpointReceiveCheck(${lastTxMap.setPoint}) <b>giving up retrying<b/>"
            lastTxMap.isSetPointReq = false
            lastTxMap.setPointRetries = 0
        }
    }
    else {
        logDebug "setpointReceiveCheck setPoint was changed successfuly to (${lastTxMap.setPoint}). No need for further checks."
        lastTxMap.setPoint = NOT_SET
        lastTxMap.isSetPointReq = false    
    }
    state.lastTx = mapToJsonString( lastTxMap )    // save everything back to state.lastTx
    state.stats  = mapToJsonString( statsMap)      // save everything back to state.stats
}

//
// Brightness checking is also called every 1 minute from receiveCheck()
def setBrightnessReceiveCheck() {
    if (settings?.resendFailed == false )
        return

    Map lastTxMap = stringToJsonMap( state.lastTx )
    if (lastTxMap.isSetBrightnessReq == false)
        return
    Map statsMap = stringToJsonMap( state.stats )
    Map lastRxMap = stringToJsonMap( state.lastRx )
    
    if (lastTxMap.setBrightness != NOT_SET && ((lastTxMap.setBrightness as String) != (lastRxMap.setBrightness as String))) {
        lastTxMap.setBrightnessRetries = (lastTxMap.setBrightnessRetries ?: 0) + 1
        if (lastTxMap.setBrightnessRetries < MaxRetries) {
            logWarn "setBrightnessReceiveCheck(${lastTxMap.setBrightness}) <b>failed<b/> (last received is still ${lastRxMap.setBrightness})"
            logDebug "resending setBrightness command : ${lastTxMap.setBrightness} (retry# ${lastTxMap.setBrightnessRetries})"
            statsMap.txFailCtr = statsMap.txFailCtr + 1
            setBrightness(lastTxMap.setBrightness)
        }
        else {
            logWarn "setBrightnessReceiveCheck(${lastTxMap.setPoint}) <b>giving up retrying<b/>"
            lastTxMap.isSetBrightnessReq = false
            lastTxMap.setBrightnessRetries = 0
        }
    }
    else {
        logDebug "setBrightnessReceiveCheck brightness was changed successfuly to (${lastTxMap.setBrightness}). No need for further checks."
        lastTxMap.setBrightness = NOT_SET
        lastTxMap.isSetBrightnessReq = false    
    }
    state.lastTx = mapToJsonString( lastTxMap )    // save everything back to state.lastTx
    state.stats  = mapToJsonString( statsMap)      // save everything back to state.stats
}




//  receiveCheck() is unconditionally scheduled Every1Minute from installed() ..
def receiveCheck() {
    modeReceiveCheck()
    setpointReceiveCheck()
    setBrightnessReceiveCheck()
}

private sendTuyaCommand(dp, dp_type, fncmd, delay=200) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, [:], delay, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    logDebug "sendTuyaCommand = ${cmds}"
    incTxCtr()
    return cmds
}

private wakeUpTuya() {
    sendZigbeeCommands(zigbee.readAttribute(0x0000, 0x0004, [:], delay=50) )
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.debug "${device.displayName} sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    incTxCtr()
    sendHubCommand(allActions)
}

private getPACKET_ID() {
    state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
    return zigbee.convertToHexString(state.packetID, 4)
}

private getDescriptionText(msg) {
    def descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) log.info "${descriptionText}"
    return descriptionText
}

def logsOff(){
    logInfo "debug logging is disabled"
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

// not used
def controlMode( mode ) {
    ArrayList<String> cmds = []
    
    switch (mode) {
        case "manual" : 
            cmds += sendTuyaCommand("02", DP_TYPE_ENUM, "00")// + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code
            logDebug "sending manual mode : ${cmds}"
            break
        case "program" :
            cmds += sendTuyaCommand("02", DP_TYPE_ENUM, "01")// + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code
            logDebug "sending program mode : ${cmds}"
            break
        default :
            break
    }
    sendZigbeeCommands( cmds )
}


def childLock( mode ) {
    ArrayList<String> cmds = []
    def dp
    if (getModelGroup() in ["AVATTO", "BEOK"]) {dp = "28"}
    else if (getModelGroup() in ["BRT-100"]) {dp = "0D"}
    else if (getModelGroup() in ["HY369"]) {dp = "07"}
    else {
        if (settings?.txtEnable) log.warn "${device.displayName} child lock mode: ${mode} is not supported for modelGroup${getModelGroup()}"
    }
    // TODO - check childLock for MOES
    if (mode == "off") {cmds += sendTuyaCommand(dp, DP_TYPE_BOOL, "00")}
    else if (mode == "on") {cmds += sendTuyaCommand(dp, DP_TYPE_BOOL, "01")}
    else {logWarn "unsupported child lock mode ${mode} !"}
    sendEvent(name: "childLock", value: mode)
    logInfo "sending child lock mode : ${mode}"
    sendZigbeeCommands( cmds )    
}


def setBrightness( bri ) {
    ArrayList<String> cmds = []
    def dp
    if (isBEOK()) {
        dp = "68"
        def key = brightnessOptions.find{it.value==bri}?.key
        logDebug "setBrightness ${bri} key=${key}"
        if (key != null) {
            def dpValHex = zigbee.convertToHexString(key as int, 2)
            cmds += sendTuyaCommand(dp, DP_TYPE_ENUM, dpValHex)            
            logDebug "changing brightness to ${bri} ($key)"
            // added 01/14/2023
            Map lastTxMap = stringToJsonMap( state.lastTx )
            lastTxMap.isSetBrightnessReq = true
            lastTxMap.setBrightness = bri
            state.lastTx = mapToJsonString( lastTxMap )    // save everything back to state.lastTx
            runIn(3, setBrightnessReceiveCheck)
            sendZigbeeCommands( cmds )    
        }
        else {
            logWarn "invalid brightness control ${bri}"
        }
    }
    else {
        logWarn "brightness control is not supported for modelGroup ${getModelGroup()}"
    }
}

def sensorSelection( sen ) {
    ArrayList<String> cmds = []
    def dp
    if (true) {
        dp = "2B"
        def key = sensorOptions.find{it.value == sen}?.key
        logDebug "sensorSelection ${sen} key=${key}"
        if (key != null) {
            def dpValHex = zigbee.convertToHexString(key as int, 2)
            cmds += sendTuyaCommand(dp, DP_TYPE_ENUM, dpValHex)            
            logDebug "changing sensor selection to ${sen} ($key)"
            sendZigbeeCommands( cmds )    
        }
        else {
            logWarn "invalid sensor selection ${sen}"
        }
    }
    else {
        logWarn "sensor selection is not supported for modelGroup ${getModelGroup()}"
    }
}

def calibration( offset ) {
    offset = 0
    ArrayList<String> cmds = []
    def dp
    if (getModelGroup() in ["AVATTO"]) dp = "1B"
    else if (getModelGroup() in ["BRT-100"]) dp = "69"
    else return;
     // callibration command returns also thermostat mode (heat), operation mode (manual), heating stetpoint and few seconds later - the temperature!
    cmds += sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(offset as int, 8))
    
    logDebug "sending calibration offset : ${offset}"
    sendZigbeeCommands( cmds )    
}

Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

def getBatteryPercentageResult(rawValue) {
    //if (settings?.logEnable) log.debug "${device.displayName} Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.value = Math.round(rawValue / 2)
        result.descriptionText = "${device.displayName} battery is ${result.value}%"
        result.isStateChange = true
        result.unit  = '%'
        sendEvent(result)
        if (settings?.txtEnable) log.info "${result.descriptionText}"
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryPercentageResult(${rawValue})"
    }
}

def zTest( dpCommand, dpValue, dpTypeString ) {
    ArrayList<String> cmds = []
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue

    log.warn " sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}

def resetStats() {
    Map stats = [
        rxCtr : 0,
        txCtr : 0,
        dupeCtr : 0,
        txFailCtr : 0
    ]
    Map lastRx = [
        dp : NOT_SET,
        fncmd : NOT_SET,    // -1
        setPoint : NOT_SET,    // -1
        setBrightness : NOT_SET
    ]
    Map lastTx = [
        mode : "unknown",
        isModeSetReq : false,
        setModeRetries: 0,
        setPoint : NOT_SET,    // -1
        setPointRetries : 0,
        isSetPointReq : false,
        setBrightness : NOT_SET,    // -1
        setBrightnessRetries : 0,
        isSetBrightnessReq : false
    ]
    state.stats  =  mapToJsonString( stats )
    state.lastRx =  mapToJsonString( lastRx )
    state.lastTx =  mapToJsonString( lastTx )
    if (txtEnable==true) log.info "${device.displayName} Statistics were reset. Press F5 to refresh the device page"
}


String mapToJsonString( Map map) {
    if (map==null || map==[:]) return ""
    String str = JsonOutput.toJson(map)
    return str
}

Map stringToJsonMap( String str) {
    if (str==null) return [:]
    def jsonSlurper = new JsonSlurper()
    def map = jsonSlurper.parseText( str )
    return map
}

private incRxCtr() {
    try {
        Map statsMap = stringToJsonMap(state.stats)
        statsMap['rxCtr'] ++
        state.stats = mapToJsonString(statsMap)
    }
    catch (e) {
        if (settings?.logEnable) log.warn "${device.displayName} incRxCtr() exception"
    }
}

private incTxCtr()     { try {Map statsMap = stringToJsonMap(state.stats); statsMap['txCtr'] ++;     state.stats = mapToJsonString(statsMap) } catch (e) {statsMap['txCtr']=0} }
private incDupeCtr()   { try {Map statsMap = stringToJsonMap(state.stats); statsMap['dupeCtr'] ++;   state.stats = mapToJsonString(statsMap) } catch (e) {statsMap['dupeCtr']=0} }
private incTxFailCtr() { Map statsMap = stringToJsonMap(state.stats); try {statsMap['txFailCtr']++ } catch (e) {statsMap['txFailCtr']=0}; state.stats = mapToJsonString(statsMap)}

private setLastRx( int dp, int fncmd) {
    try {
        Map lastRxMap = stringToJsonMap(state.lastRx)
        lastRxMap['dp'] = dp
        lastRxMap['fncmd'] = fncmd
        state.lastRx = mapToJsonString(lastRxMap)
    }
    catch (e) {
        if (settings?.logEnable) log.warn "${device.displayName} setLastRx() exception"
    }
}

private setLastTx( String mode=null, Boolean isModeSetReq=null) {
    try {
        Map lastTxMap = stringToJsonMap(state.lastTx)
        if (mode != null) {
            lastTxMap['mode'] = mode
        }
        if (isModeSetReq != null) {
            lastTxMap['isModeSetReq'] = isModeSetReq
        }
        state.lastTx = mapToJsonString(lastTxMap)
    }
    catch (e) {
        if (settings?.logEnable) log.warn "${device.displayName} setLastTx() exception"
    }
}

def getLastMode() {
    try {    
        Map lastTx = stringToJsonMap( state.lastTx )
        return lastTx.mode
    }
    catch (e) {
        if (settings?.logEnable) log.warn "${device.displayName} getLastMode() exception"
        return "exception"
    }
}

def isDuplicated( int dp, int fncmd ) {
    Map oldDpFncmd = stringToJsonMap( state.lastRx )
    if (dp == oldDpFncmd.dp && fncmd == oldDpFncmd.fncmd)
        return true
    else
        return false
}

def logDebug(msg) {
    if (settings?.logEnable == null || settings?.logEnable == true) {
        log.debug "${device.displayName} " + msg
    }
}

def logInfo(msg) {
    if (settings?.txtEnable == null || settings?.txtEnable == true) {
        log.info "${device.displayName} " + msg
    }
}

def logWarn(msg) {
    if (settings?.logEnable == null || settings?.logEnable == true) {
        log.warn "${device.displayName} " + msg
    }
}

     
     
def test() {
    incRxCtr()
    def a = 5
}


/*
    BRT-100 Zigbee network re-pair procedure: After the actuator has completed self-test, long press [X] access to interface, short press '+' to choose WiFi icon,
        short press [X] to confirm this option, long press [X]. WiFi icon will start flashing when in pairing mode.

*/
