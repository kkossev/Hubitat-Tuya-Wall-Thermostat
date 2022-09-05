/**
 *  Tuya Wall Thermostat driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/release-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050 
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
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
 * ver. 1.2.3 2022-09-05 kkossev  - (dev branch) added FactoryReset command; added AVATTO programMode preference; 
 *                                  TODO:  add forceOn; add Frost protection mode? ; add sensorMode for AVATTO?
 *
*/

def version() { "1.2.3" }
def timeStamp() {"2022/09/05 11:01 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol

@Field static final Boolean debug = true

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
        capability "Battery"                    // BRT-100
        
        attribute "childLock", "enum", ["off", "on"]

        if (debug == true) {
            command "factoryReset", [[name:"factoryReset", type: "STRING", description: "Type 'YES'", constraints: ["STRING"]]]
            command "zTest", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] 
            ]
        }
        command "initialize", [[name: "Initialize the thermostat after switching drivers.  \n\r   ***** Will load device default values! *****" ]]
        command "childLock",  [[name: "ChildLock", type: "ENUM", constraints: ["off", "on"], description: "Select Child Lock mode"] ]        
        
        // (AVATTO)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ye5jkfsb",  deviceJoinName: "AVATTO Wall Thermostat" // ME81AH 
        // (Moes)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_aoclfnxz",  deviceJoinName: "Moes Wall Thermostat" // BHT-002
        // (unknown)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_unknown",  deviceJoinName: "_TZE200_ Thermostat" // unknown
        // (BEOK)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_2ekuz3dz",  deviceJoinName: "Beok Wall Thermostat" // 
        // (BRT-100 for dev tests only!)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_b6wax7g0",  deviceJoinName: "BRT-100 TRV" // BRT-100
        //fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_chyvmhay",  deviceJoinName: "Lidl Silvercrest" // Lidl Silvercrest (dev tests only)
        
    }
    preferences {
        if (logEnable == true || logEnable == false) { 
            input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: false)
            input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
            input (name: "forceManual", type: "bool", title: "<b>Force Manual Mode</b>", description: "<i>If the thermostat changes intto schedule mode, then it automatically reverts back to manual mode</i>", defaultValue: false)
            input (name: "resendFailed", type: "bool", title: "<b>Resend failed commands</b>", description: "<i>If the thermostat does not change the Setpoint or Mode as expected, then commands will be resent automatically</i>", defaultValue: false)
            input (name: "minTemp", type: "number", title: "Minimim Temperature", description: "<i>The Minimim temperature setpoint that can be sent to the device</i>", defaultValue: 10, range: "5.0..20.0")
            input (name: "maxTemp", type: "number", title: "Maximum Temperature", description: "<i>The Maximum temperature setpoint that can be sent to the device</i>", defaultValue: 40, range: "28.0..90.0")
            input (name: "modelGroupPreference", title: "Select a model group. Recommended value is <b>'Auto detect'</b>", /*description: "<i>Thermostat type</i>",*/ type: "enum", options:["Auto detect", "AVATTO", "MOES", "BEOK", "MODEL3", "BRT-100"], defaultValue: "Auto detect", required: false)        
            input (name: "tempCalibration", type: "number", title: "Temperature Calibration", description: "<i>Adjust measured temperature range: -9..9 C</i>", defaultValue: 0, range: "-9.0..9.0")
            input (name: "hysteresis", type: "number", title: "Hysteresis", description: "<i>Adjust switching differential range: 1..5 C</i>", defaultValue: 1, range: "1.0..5.0")        // not available for BRT-100 !
            if (getModelGroup() in ['AVATTO'])  {
                input (name: "programMode", type: "enum", title: "Program Mode (thermostat internal schedule)", description: "<i>Recommended selection is '<b>off</b>'</i>", defaultValue: 0, options: [0:"off", 1:"Mon-Fri", 2:"Mon-Sat", 3: "Mon-Sun"])
            }
        }
    }
}



@Field static final Map<String, String> Models = [
    '_TZE200_ye5jkfsb'  : 'AVATTO',      // Tuya AVATTO ME81AH 
    '_TZE200_aoclfnxz'  : 'MOES',        // Tuya Moes BHT series Thermostat BTH-002
    '_TZE200_2ekuz3dz'  : 'BEOK',        // Beok thermostat
    '_TZE200_other'     : 'MODEL3',      // Tuya other models (reserved)
    '_TZE200_b6wax7g0'  : 'BRT-100',     // TRV BRT-100; ZONNSMART
    '_TZE200_ckud7u2l'  : 'TEST2',       // KKmoon Tuya; temp /10.0
    '_TZE200_zion52ef'  : 'TEST3',       // TRV MOES => fn = "0001 > off:  dp = "0204"  data = "02" // off; heat:  dp = "0204"  data = "01" // on; auto: n/a !; setHeatingSetpoint(preciseDegrees):   fn = "00" SP = preciseDegrees *10; dp = "1002"
    '_TZE200_c88teujp'  : 'TEST3',       // TRV "SEA-TR", "Saswell", model "SEA801" (to be tested)
    '_TZE200_xxxxxxxx'  : 'UNKNOWN',     
    '_TZE200_xxxxxxxx'  : 'UNKNOWN',     
    ''                  : 'UNKNOWN'      // 
]

private PROGRAM_MODE_VALUE(mode) { mode == "off" ? 0 : mode ==  "Mon-Fri" ? 1 : mode == "Mon-Sat" ? 2 : mode == "Mon-Sun" ? 3 : null }
private PROGRAM_MODE_NAME(value) { value == 0 ? "off" : value == 1 ? "Mon-Fri" : value == 2 ? "Mon-Sat" : value == 3 ? "Mon-Sun" : null }


@Field static final Integer MaxRetries = 3
                                
// KK TODO !
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
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1
    //if (settings?.logEnable) log.debug "${device.displayName} parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
            if (settings?.logEnable) log.debug "${device.displayName} time synchronization request from device, descMap = ${descMap}"
            def offset = 0
            try {
                offset = location.getTimeZone().getOffset(new Date().getTime())
                //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
            } catch(e) {
                log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
            }
            def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
            // log.trace "${device.displayName} now is: ${now()}"  // KK TODO - converto to Date/Time string!        
            if (settings?.logEnable) log.debug "${device.displayName} sending time data : ${cmds}"
            cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
            if (state.txCounter != null) state.txCounter = state.txCounter + 1
            state.old_dp = ""
            state.old_fncmd = ""
            
        } else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
            String clusterCmd = descMap?.data[0]
            def status = descMap?.data[1]            
            if (settings?.logEnable) log.debug "${device.displayName} device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
            state.old_dp = ""
            state.old_fncmd = ""
            if (status != "00") {
                if (settings?.logEnable) log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} group = ${getModelGroup()} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
            }
            
        } else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02")) {
            //if (descMap?.command == "02") { if (settings?.logEnable) log.warn "command == 02 !"  }   
            def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
            def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
            def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
            def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
            if (dp == state.old_dp && fncmd == state.old_fncmd) {
                if (settings?.logEnable) log.warn "(duplicate) transid=${transid} dp_id=${dp_id} <b>dp=${dp}</b> fncmd=${fncmd} command=${descMap?.command} data = ${descMap?.data}"
                if ( state.duplicateCounter != null ) state.duplicateCounter = state.duplicateCounter +1
                return
            }
            if (settings?.logEnable) log.trace " dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
            state.old_dp = dp
            state.old_fncmd = fncmd
            // the switch cases below default to dp_id = "01"
            switch (dp) {
                case 0x01 :  // (01) switch state : Heat / Off        DP_IDENTIFIER_THERMOSTAT_MODE_4 0x01 // mode for Moes device used with DP_TYPE_ENUM
                    if (getModelGroup() in ['BRT-100', 'TEST2']) {
                        processBRT100Presets( dp, fncmd )                       // 0x0401 # Mode (Received value 0:Manual / 1:Holiday / 2:Temporary Manual Mode / 3:Prog)
                    }
                    else {    // AVATTO switch (boolean) // state
                        /* version 1.0.4 */
                        //def mode = (fncmd == 0) ? "off" : "heat"
                        def mode = (fncmd == 0) ? "off" : state.lastThermostatMode    // version 1.2.3
                        if (settings?.logEnable) {log.info "${device.displayName} Thermostat mode (switch state) reported is: ${mode} (dp=${dp}, fncmd=${fncmd})"}
                        else if (settings?.txtEnable) {log.info "${device.displayName} Thermostat mode (switch state) reported is: ${mode}"}
                        sendEvent(name: "thermostatMode", value: mode, displayed: true)
                        if (mode == "off") {
                            sendEvent(name: "thermostatOperatingState", value: "idle", displayed: true)    // do not store as last state!
                        }
                        else {
                            // do not store if off !
                            state.lastThermostatMode = mode
                            sendEvent(name: "thermostatOperatingState", value: state.lastThermostatOperatingState, displayed: true)    // do not store as last state!
                        }                        
                        if (mode == state.mode) {
                            state.mode = ""
                        }
                    }
                    break
                case 0x02 : // Mode (LIDL)                                  // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT 0x02 // Heatsetpoint
                    if (settings?.logEnable) log.trace " dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    if (getModelGroup() in ['BRT-100', 'TEST2']) {             // BRT-100 Thermostat heatsetpoint # 0x0202 #
                        processTuyaHeatSetpointReport( fncmd )              // target temp, in degrees (int!)
                        break
                    }
                    else {    // AVATTO : mode (enum) 'manual', 'program'
                        // DP_IDENTIFIER_THERMOSTAT_MODE_2 0x02 // mode for Moe device used with DP_TYPE_ENUM
                        if (settings?.logEnable) log.trace "device current mode = ${device.currentState('thermostatMode').value}"
                        if (device.currentState("thermostatMode").value == "off") {
                            if (settings?.logEnable) log.warn "ignoring 0x02 command in off mode"
                            sendEvent(name: "thermostatOperatingState", value: "idle")
                            break    // ignore 0x02 command if thermostat was switched off !!
                        }
                        else {
                            // continue below.. break statement is missing intentionaly!
                            if (settings?.logEnable) log.trace "...continue in mode ${device.currentState('thermostatMode').value}..."
                        }
                    }
                case 0x03 :    // Scheduled/Manual Mode or // Thermostat current temperature (in decidegrees)    // working status
                    if (settings?.logEnable) log.trace "processing command dp=${dp} fncmd=${fncmd}"
                    // TODO - use processTuyaModes3( dp, fncmd )
                    if (descMap?.data.size() <= 7) {
                        def mode
                        if (!(fncmd == 0)) {        // KK inverted
                            mode = "auto"    // scheduled
                            //log.trace "forceManual = ${settings?.forceManual}"
                            if (settings?.forceManual == true) {
                                if (settings?.txtEnable) log.warn "${device.displayName} 'Force Manual Mode' preference option is enabled, switching back to heat mode!"
                                setManualMode()
                            }
                            else {
                                //log.trace "setManualMode() <b>not called!</b>"
                            }
                        } else {
                            mode = "heat"    // manual
                        }
                        if (settings?.logEnable) {log.info "${device.displayName} Thermostat mode (working status) reported is: $mode (dp=${dp}, fncmd=${fncmd})"}
                        else if (settings?.txtEnable) {log.info "${device.displayName} Thermostat mode (working status) reported is: ${mode}"}
                        sendEvent(name: "thermostatMode", value: mode, displayed: true)    // mode was confirmed from the Preset info data...
                        state.lastThermostatMode = mode
                    } 
                    else {    // # 0x0203 # BRT-100
                        // Thermostat current temperature
                        if (settings?.logEnable) log.trace "processTuyaTemperatureReport descMap?.size() = ${descMap?.data.size()} dp_id=${dp_id} <b>dp=${dp}</b> :"
                        processTuyaTemperatureReport( fncmd )
                    }
                    break
                case 0x04 :    // BRT-100 Boost    DP_IDENTIFIER_THERMOSTAT_BOOST    DP_IDENTIFIER_THERMOSTAT_BOOST 0x04 // Boost for Moes
                    processTuyaBoostModeReport( fncmd )
                    break
                case 0x05 :    // BRT-100 ?
                    if (settings?.txtEnable) log.info "${device.displayName} configuration is done. Result: 0x${fncmd}"
                    break
                case 0x07 :    // others Childlock status    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_1 0x07    // 0x0407 > starting moving     // sound for X5H thermostat
                    if (settings?.txtEnable) log.info "${device.displayName} valve starts moving: 0x${fncmd}"    // BRT-100  00-> opening; 01-> closed!
                    if (fncmd == 00) {
                        sendThermostatOperatingStateEvent("heating")
                        //sendEvent(name: "thermostatOperatingState", value: "heating", displayed: true)
                    }
                    else {    // fncmd == 01
                        sendThermostatOperatingStateEvent("idle")
                        //sendEvent(name: "thermostatOperatingState", value: "idle", displayed: true)
                    }
                    break
                case 0x08 :    // DP_IDENTIFIER_WINDOW_OPEN2 0x08    // BRT-100
                    if (settings?.txtEnable) log.info "${device.displayName} Open window detection MODE (dp=${dp}) is: ${fncmd}"    //0:function disabled / 1:function enabled
                    break
                // case 0x09 : // BRT-100 unknown function
                case 0x0D :    // 0x0D (13) BRT-100 Childlock status    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_4 0x0D MOES, LIDL
                    if (settings?.txtEnable) log.info "${device.displayName} Child Lock (dp=${dp}) is: ${fncmd}"    // 0:function disabled / 1:function enabled
                    break
                case 0x10 :    // (16): Heating setpoint AVATTO
                    // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT_3 0x10         // Heatsetpoint for TRV_MOE mode heat // BRT-100 Rx only?
                    processTuyaHeatSetpointReport( fncmd )
                    break
                case 0x11 :    // (17) AVATTO
                    if (settings?.txtEnable) log.info "${device.displayName} Set temperature F is: ${fncmd}"
                    break
                case 0x12 :    // (18) Max Temp Limit MOES, LIDL
                    if (getModelGroup() in ['AVATTO'])  {
                        if (settings?.txtEnable) log.info "${device.displayName} Set temperature upper limit F is: ${fncmd}"
                    }
                    else {    // KK TODO - also Window open status (false:true) for TRVs ?    DP_IDENTIFIER_WINDOW_OPEN
                        if (settings?.txtEnable) log.info "${device.displayName} Max Temp Limit is: ${fncmd}"
                    }
                    break
                case 0x13 :    // (19) Max Temp LIMIT AVATTO MOES, LIDL
                    if (getModelGroup() in ['AVATTO']) {
                        device.updateSetting("maxTemp", fncmd)    // aka 'temperature ceiling'
                    }
                    if (settings?.txtEnable) log.info "${device.displayName} Max Temp Limit is: ${fncmd} C (dp=${dp}, fncmd=${fncmd})"
                    break
                case 0x14 :    //  (20) Dead Zone Temp (hysteresis) MOES, LIDL
                    if (getModelGroup() in ['AVATTO'])  {
                        if (settings?.txtEnable) log.info "${device.displayName} lower limit F is: ${fncmd}"
                    }
                    else {    // KK TODO - also Valve state report : on=1 / off=0 ?  DP_IDENTIFIER_THERMOSTAT_VALVE 0x14 // Valve
                        if (settings?.txtEnable) log.info "${device.displayName} Dead Zone Temp (hysteresis) is: ${fncmd}"
                    }
                    break
                case 0x0E :    // (14) BRT-100 Battery # 0x020e # battery percentage (updated every 4 hours )
                case 0x15 :    // (21)
                    def battery = fncmd >100 ? 100 : fncmd
                    if (settings?.txtEnable) log.info "${device.displayName} battery is: ${fncmd} % (dp=${dp})"
                    getBatteryPercentageResult(fncmd*2)                
                    break
                case 0x17 :    // (23) temperature scale for AVATTO
                    if (settings?.txtEnable) log.info "${device.displayName} temperature scale is: ${fncmd==0?'C':'F'} (${fncmd})"
                    break
                case 0x18 :    // (24) : Current (local) temperature
                    if (settings?.logEnable) log.trace "processTuyaTemperatureReport dp_id=${dp_id} <b>dp=${dp}</b> :"
                    processTuyaTemperatureReport( fncmd )
                    break
                case 0x1A :    // (26) AVATTO setpoint lower limit
                    if (getModelGroup() in ['AVATTO']) {
                        device.updateSetting("minTemp", fncmd)
                    }
                    if (settings?.txtEnable) log.info "${device.displayName} Min temperature limit is: ${fncmd} C (dp=${dp}, fncmd=${fncmd})"
                    // TODO - update the minTemp preference !
                    break
                case 0x1B :    // (27) temperature calibration/correction (offset in degrees) for AVATTO, Moes and Saswell
                    processTuyaCalibration( dp, fncmd )
                    break
                case 0x1D :    // (29) AVATTO
                    if (settings?.txtEnable) log.info "${device.displayName} current temperature F is: ${fncmd}"
                    break
                case 0x23 :    // (35) LIDL BatteryVoltage
                    if (settings?.txtEnable) log.info "${device.displayName} BatteryVoltage is: ${fncmd}"
                    break
                case 0x24 :    // (36) : current (running) operating state (valve) AVATTO (enum) 'open','close'
                    if (settings?.txtEnable) {log.info "${device.displayName} thermostatOperatingState is: ${fncmd==0 ? 'heating' : 'idle'}"}
                    else if (settings?.logEnable) {log.info "${device.displayName} thermostatOperatingState is: ${fncmd==0 ? 'heating' : 'idle'} (dp=${dp}, fncmd=${fncmd})"}
                    sendThermostatOperatingStateEvent(fncmd==0 ? "heating" : "idle")
                    break
                case  0x27 :    // (39) AVATTO - RESET
                    if (settings?.txtEnable) log.info "${device.displayName} thermostat reset (${fncmd})"
                    break
                case 0x1E :    // (30) DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_3 0x1E // For Moes device
                case 0x28 :    // (40) Child Lock    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_2 0x28 ( AVATTO (boolean) )
                    if (settings?.txtEnable) log.info "${device.displayName} Child Lock is: ${fncmd} (dp=${dp})"
                    sendEvent(name: "childLock", value: (fncmd == 0) ? "off" : "on" )
                    break
                case 0x2B :    // (43) AVATTO Sensor 0-In 1-Out 2-Both    // KK TODO
                    if (settings?.txtEnable) log.info "${device.displayName} Sensor is: ${fncmd==0?'In':fncmd==1?'Out':fncmd==2?'In and Out':'UNKNOWN'} (${fncmd})"
                    break
                case 0x2C :                                                 // temperature calibration (offset in degree)   //DP_IDENTIFIER_THERMOSTAT_CALIBRATION_2 0x2C // Calibration offset used by others
                    processTuyaCalibration( dp, fncmd )
                    break
                case 0x2D :    // (45) LIDL and AVATTO ErrorStatus (bitmap) e1, e2, e3 // er1: Built-in sensor disconnected or fault with it; Er1: Built-in sensor disconnected or fault with it.
                    if (settings?.txtEnable) log.info "${device.displayName} error code : ${fncmd}"
                    break
                // case 0x62 : // (98) DP_IDENTIFIER_REPORTING_TIME 0x62 (Sensors)
                case 0x65 :    // (101) AVATTO PID ; also LIDL ComfortTemp
                    if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} Thermostat PID regulation point is: ${fncmd}"    // Model#1 only !!
                    }
                    else {
                        if (settings?.logEnable) log.info "${device.displayName} Thermostat SCHEDULE_1 data received (not processed)..."
                    }
                    break
                case 0x66 :     // (102) min temperature limit; also LIDL EcoTemp
                    if (settings?.txtEnable) log.info "${device.displayName} Min temperature limit is: ${fncmd}"
                    break
                case 0x67 :    // (103) max temperature limit; also LIDL AwaySetting
                    if (getModelGroup() in ['BRT-100']) {                      // #0x0267 # Boost heating countdown in second (Received value [0, 0, 1, 44] for 300)
                        if (settings?.txtEnable) log.info "${device.displayName} Boost heating countdown: ${fncmd} seconds"
                    }
                    else if (getModelGroup() in ['AVATTO']) {     // Antifreeze mode ?
                        if (settings?.txtEnable) log.info "${device.displayName} Antifreeze mode is ${fncmd==0?'off':'on'} (${fncmd})"
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} unknown parameter is: ${fncmd} (dp=${dp}, fncmd=${fncmd}, data=${descMap?.data})"
                    }
                    // KK TODO - could be setpoint for some devices ?
                    // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT_2 0x67 // Heatsetpoint for Moe ?
                    break
                case 0x68 :     // (104) DP_IDENTIFIER_THERMOSTAT_VALVE_2 0x68 // Valve; also LIDL TempCalibration!
                    if (getModelGroup() in ['AVATTO']) {
                        def value = safeToInt(fncmd)
                        if (settings?.txtEnable) log.info "${device.displayName} AVATTO Program Mode (104) received is: ${PROGRAM_MODE_NAME(value)} (${fncmd})"      // AVATTO programm mode 0:0ff 1:Mon-Fri 2:Mon-Sat 3:Mon-Sun    
                        device.updateSetting( "programMode",  [value:value.toString(), type:"enum"] )
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} Valve position is: ${fncmd}% (dp=${dp}, fncmd=${fncmd})"
                        // # 0x0268 # TODO - send event! (works OK with BRT-100 (values of 25 / 50 / 75 / 100) 
                    }
                    break
                case 0x69 :     // (105) BRT-100 temp calibration // could be also Heatsetpoint for TRV_MOE mode auto ? also LIDL
                    if (getModelGroup() in ['BRT-100']) {
                        processTuyaCalibration( dp, fncmd )
                    }
                    else if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} AVATTO unknown parameter (105) is: ${fncmd}"      // TODO: check AVATTO usage                                                 
                    }
                    else {
                         log.warn "${device.displayName} (DP=0x69) ?TRV_MOES auto mode Heatsetpoint? value is: ${fncmd}"
                    }
                    break
                case 0x6A :     // (106) DP_IDENTIFIER_THERMOSTAT_MODE_1 0x6A // mode used with DP_TYPE_ENUM    Energy saving mode (Received value 0:off / 1:on)
                    if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} Dead Zone temp (hysteresis) is: ${fncmd}C (dp=${dp}, fncmd=${fncmd})"
                        device.updateSetting("hysteresis", fncmd)
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} Energy saving mode? (dp=${dp}) is: ${fncmd} data = ${descMap?.data})"    // 0:function disabled / 1:function enabled
                    }
                    break
                case 0x6B :    // (107) DP_IDENTIFIER_TEMPERATURE 0x6B (Sensors)      // BRT-100 !
                    if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} AVATTO unknown parameter (105) is: ${fncmd}"      // TODO: check AVATTO usage                                                 
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6B) Energy saving mode temperature value is: ${fncmd}"    // for BRT-100 # 0x026b # Energy saving mode temperature ( Received value [0, 0, 0, 15] )
                    }
                    break
                case 0x6C :    // (107)                                             
                    if (getModelGroup() in ['BRT-100']) {  
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6C) Max target temp is: ${fncmd}"        // BRT-100 ( Received value [0, 0, 0, 35] )
                        device.updateSetting("maxTemp", fncmd)
                    }
                    else if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} AVATTO unknown parameter (107) is: ${fncmd}"      // TODO: check AVATTO usage                                                 
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6C) humidity value is: ${fncmd}"        // DP_IDENTIFIER_HUMIDITY 0x6C  (Sensors)
                    }
                    // KK Tuya cmd: dp=108 value=404095046 descMap.data = [00, 08, 6C, 00, 00, 18, 06, 00, 28, 08, 00, 1C, 0B, 1E, 32, 0C, 1E, 32, 11, 00, 18, 16, 00, 46, 08, 00, 50, 17, 00, 3C]
                    break
                case 0x6D :    // (108)                                                 
                    if (getModelGroup() in ['BRT-100']) {                      // 0x026d # Min target temp (Received value [0, 0, 0, 5])
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6D) Min target temp is: ${fncmd}"
                        device.updateSetting("minTemp", fncmd)
                    }
                    else if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} AVATTO unknown parameter (108) is: ${fncmd}"      // TODO: check AVATTO usage                                                 
                    }
                    else {                                                    // Valve position in % (also // DP_IDENTIFIER_THERMOSTAT_SCHEDULE_4 0x6D // Not finished)
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6D) valve position is: ${fncmd} (dp=${dp}, fncmd=${fncmd})"
                    }
                    // KK TODO if (valve > 3) => On !
                    break
                case 0x6E :    	// (110) Low battery    DP_IDENTIFIER_BATTERY 0x6E
                    if (settings?.txtEnable) log.info "${device.displayName} Battery (DP= 0x6E) is: ${fncmd}"
                    break
                case 0x70 :    // (112) // Reporting    DP_IDENTIFIER_REPORTING 0x70
                    // DP_IDENTIFIER_THERMOSTAT_SCHEDULE_2 0x70 // work days (6)
                    if (settings?.txtEnable) log.info "${device.displayName} reporting status state : ${descMap?.data}"
                    break
                //case 0x71 :// DP_IDENTIFIER_THERMOSTAT_SCHEDULE_3 0x71 // holiday = Not working day (6)
                // case 0x74 :  // 0x74(116)- LIDL OpenwindowTemp
                // case 0x75 :  // 0x75(117) - LIDL OpenwindowTime
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



def processTuyaHeatSetpointReport( fncmd )
{                        
    def setpointValue
    def model = getModelGroup()
    switch (model) {
        case 'AVATTO' :
            setpointValue = fncmd
            break
        case 'MOES' :
            setpointValue = fncmd
            break
        case 'BEOK' :
            setpointValue = fncmd / 10.0
            break
        case 'MODEL3' :
            setpointValue = fncmd
            break
        case 'BRT-100' :
            setpointValue = fncmd
            break
        case 'TEST2' :
        case 'TEST3' :
            setpointValue = fncmd / 10.0
            break
        case 'UNKNOWN' :
        default :
            setpointValue = fncmd
            break
    }
    if (settings?.txtEnable) log.info "${device.displayName} heatingSetpoint is: ${setpointValue}"+"\u00B0"+"C"
    sendEvent(name: "heatingSetpoint", value: setpointValue as int, unit: "\u00B0"+"C", displayed: true)
    sendEvent(name: "thermostatSetpoint", value: setpointValue as int, unit: "\u00B0"+"C", displayed: false)        // Google Home compatibility
    if (setpointValue == state.setpoint)  {
        state.setpoint = 0
    }                        
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
        case 'MODEL3' :
            currentTemperatureValue = fncmd / 10.0     
            break
        case 'BRT-100' :
        case 'TEST2' :
        case 'TEST3' :
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
    sendEvent(name: "temperature", value: currentTemperatureValue, unit: "\u00B0"+"C", displayed: true)
}

def processTuyaCalibration( dp, fncmd )
{
    def temp = fncmd 
    if (getModelGroup() in ['AVATTO'] ){    // (dp=27, fncmd=-1)
        device.updateSetting("tempCalibration", temp)
        //log.trace "AVATTO calibration"
    }
    else  if (getModelGroup() in ['BRT-100'] && dp == 105) { // 0x69
        device.updateSetting("tempCalibration", temp)
        if (settings?.logEnable) log.trace "BRT-100 calibration"
    }
    else {    // "_TZE200_aoclfnxz"
        if (settings?.logEnable) log.trace "other calibration, getModelGroup() = ${getModelGroup()} dp=${dp} fncmd = ${fncmd}"
        if (temp > 2048) {
            temp = temp - 4096;
        }
        temp = temp / 100        // KK - check !
    }
    if (settings?.txtEnable) log.info "${device.displayName} temperature calibration (correction) is: ${temp} (dp=${dp}, fncmd=${fncmd}) "
}

def processBRT100Presets( dp, data ) {
    if (settings?.logEnable) log.trace "processBRT100Presets fp-${dp} data=${data}"
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

    sendEvent(name: "thermostatMode", value: mode, displayed: true)    // mode was confirmed from the Preset info data...
    state.lastThermostatMode = mode
    
    // TODO - change tehrmostatPreset depending on preset ?
    if (settings?.txtEnable) log.info "${device.displayName} BRT-100 Presets: mode = ${mode} preset = ${preset}"
}

def processTuyaModes3( dp, data ) {
    // dp = 0x0402 : // preset for moes or mode
    // dp = 0x0403 : // preset for moes    
    if (getModelGroup() in ['BRT-100', 'TEST2']) {    // BRT-100 ?    KK: TODO!
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

def processTuyaModes4( dp, data ) {
    // TODO - check for model !
    // dp = 0x0403 : // preset for moes    
    if (true) {   
        def mode
        if (data == 0) { mode = "holiday" } 
        else if (data == 1) { mode = "auto" }
        else if (data == 2) { mode = "manual" }
        else if (data == 3) { mode = "manual" }
        else if (data == 4) { mode = "eco" }
        else if (data == 5) { mode = "boost" }
        else if (data == 6) { mode = "complex" }
        else {
            if (settings?.logEnable) log.warn "${device.displayName} processTuyaModes4: unknown mode: ${data}"
            return
        }
        if (settings?.txtEnable) log.info "${device.displayName} mode is: ${mode}"
        // TODO - - change thremostatMode depending on mode ?
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} processTuyaModes4: model manufacturer ${device.getDataValue('manufacturer')} group = ${getModelGroup()}"
            return
    }
}

def processTuyaBoostModeReport( fncmd )
{
    def boostMode = fncmd == 0 ? "off" : "on"                // 0:"off" : 1:"boost in progress"
    if (settings?.txtEnable) log.info "${device.displayName} Boost mode is: $boostMode (0x${fncmd})"
    if (boostMode == "on") {
        sendEvent(name: "thermostatMode", value: "emergency heat", displayed: false)
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
    sendEvent(name: "thermostatOperatingState", value: st, displayed: true)
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
    //sendEvent(name: "thermostatOperatingState", value: guessThermostatOperatingState(), displayed: false)
    cmds = sendTuyaCommand("04", DP_TYPE_BOOL, "00")
    sendZigbeeCommands( cmds )    
}

def sendTuyaThermostatMode( mode ) {
    ArrayList<String> cmds = []
    def dp = ""
    def fn = ""   
    def model = getModelGroup()
    switch (mode) {
        case "off" :    // 
            if (model in ['AVATTO', 'MOES', 'BEOK', 'MODEL3']) {
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
            else {    // all other models
                dp = "04"                            
                fn = "00"    // not tested !
            }
            break
        case "heat" :    // manual mode
            if (model in ['AVATTO', 'MOES', 'BEOK', 'MODEL3']) {    // TODO - this command only does not switch off Scheduled (auto) mode !
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
            else {    // all other models    // not tested!
                dp = "04"                            
                fn = "01"    // not tested !
            }
            break
        case "auto" :    // scheduled mode
            if (settings?.logEnable) log.trace "sending AUTO mode!"
            if (model in ['AVATTO', 'MOES', 'BEOK', 'MODEL3']) {    // TODO - does not switch off manual mode ?
                if (device.currentState('thermostatMode').value == "off") {
                    cmds += switchThermostatOn()
                }
                dp = "02"    // was "01"
                fn = "01"     // was "02"
                //                return sendTuyaCommand(dp, DP_TYPE_ENUM, fn)    
            }
            else if (model in ['BRT-100']) {
                dp = "01"                       
                fn = "00"  
                return sendTuyaCommand(dp, DP_TYPE_ENUM, fn)    // BRT-100 DP=1 needs DP_TYPE_ENUM!
            }
            else {    // all other models    // not tested!
                dp = "01"                            
                fn = "01"    // not tested ! (same as heat)
            }
            break
        case "emergency heat" :
            state.mode = "emergency heat"
            if (model in ['BRT-100']) {    // BRT-100
                dp = "04"                            
                fn = "01"
            }
            else {    // all other models    // not tested!
                dp = "04"                            
                fn = "01"    // not tested !
            }       
            break
        case "cool" :
            state.mode = "cool"
            return null
            break
        default :
            log.warn "Unsupported mode ${mode}"
            return null
    }
    cmds += sendTuyaCommand(dp, DP_TYPE_BOOL, fn)
    sendZigbeeCommands( cmds )
}

//  sends TuyaCommand and checks after 4 seconds
def setThermostatMode( mode ) {
    if (settings?.logEnable) log.debug "${device.displayName} sending setThermostatMode(${mode})"
    state.mode = mode
    runIn(4, modeReceiveCheck)
    return sendTuyaThermostatMode( mode ) // must be last command!
}



def sendTuyaHeatingSetpoint( temperature ) {
    if (settings?.logEnable) log.debug "${device.displayName} sendTuyaHeatingSetpoint(${temperature})"
    def settemp = temperature as int           // KK check! 
    def dp = "10"
    def model = getModelGroup()
    switch (model) {
        case 'AVATTO' :                          // AVATTO - only integer setPoints!
            dp = "10"
            settemp = temperature
            break
        case 'MOES' :                            // MOES - 0.5 precision? ( and double the setpoint value ? )
            dp = "10"
            settemp = temperature                // KK check!
            break
        case 'BEOK' :                            // 
            dp = "10"
            settemp = temperature * 10               
            break
        case 'MODEL3' :
            dp = "10"
            settemp = temperature
            break
        case 'BRT-100' :                            // BRT-100
            dp = "02"                            
            settemp = temperature
        case 'TEST2' :
        case 'TEST3' :
            //dp = "02"
            settemp = temperature
            break
        default :
            settemp = temperature
            break
    }    
    // iquix code 
    //settemp += (settemp != temperature && temperature > device.currentValue("heatingSetpoint")) ? 1 : 0        // KK check !
    if (settings?.logEnable) log.debug "${device.displayName} changing setpoint to ${settemp}"
    state.setpoint = temperature    // KK was settemp !! CHECK !
    runIn(4, setpointReceiveCheck)
    sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(settemp as int, 8))
}

def setThermostatSetpoint( temperature ) {
    setHeatingSetpoint( temperature )
}

//  ThermostatHeatingSetpoint command
//  sends TuyaCommand and checks after 4 seconds
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface)
def setHeatingSetpoint( temperature ) {
    def previousSetpoint = device.currentState('heatingSetpoint').value as int
    if (settings?.logEnable) log.trace "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})"
    if (temperature != (temperature as int)) {
        if (temperature > previousSetpoint) {
            temperature = (temperature + 0.5 ) as int
        }
        else {
            temperature = temperature as int
        }
        
        if (settings?.logEnable) log.trace "corrected heating setpoint${temperature}"
    }  
    if (settings?.maxTemp == null || settings?.minTemp == null ) { device.updateSetting("minTemp", 5);  device.updateSetting("maxTemp", 28)   }
    if (temperature > settings?.maxTemp.value ) temperature = settings?.maxTemp.value
    if (temperature < settings?.minTemp.value ) temperature = settings?.minTemp.value
    
    sendEvent(name: "heatingSetpoint", value: temperature as int, unit: "\u00B0"+"C", displayed: true)
    sendEvent(name: "thermostatSetpoint", value: temperature as int, unit: "\u00B0"+"C", displayed: true)
    updateDataValue("lastRunningMode", "heat")
    
    state.heatingSetPointRetry = 0
    sendTuyaHeatingSetpoint( temperature )
}


def setCoolingSetpoint(temperature){
    if (settings?.logEnable) log.debug "${device.displayName} setCoolingSetpoint(${temperature}) called!"
    if (temperature != (temperature as int)) {
        temperature = (temperature + 0.5 ) as int
        if (settings?.logEnable) log.trace "corrected temperature: ${temperature}"
    }
    sendEvent(name: "coolingSetpoint", value: temperature, unit: "\u00B0"+"C", displayed: false)
    // setHeatingSetpoint(temperature)    // KK check!
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
def cool() { setThermostatMode("off") }
def fanAuto() { setThermostatFanMode("auto") }
def fanCirculate() { setThermostatFanMode("circulate") }
def fanOn() { setThermostatFanMode("on") }
def setSchedule(schedule) { if (settings?.logEnable) log.debug "${device.displayName} setSchedule (${schedule}) called!"} 

def setManualMode() {
    if (settings?.logEnable) log.debug "${device.displayName} setManualMode()"
    ArrayList<String> cmds = []
    cmds = sendTuyaCommand("02", DP_TYPE_ENUM, "00") + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code
    sendZigbeeCommands( cmds )
}

def switchThermostatOn() {
    if (settings?.logEnable) log.debug "${device.displayName} switching On!"
    ArrayList<String> cmds = []
    cmds = sendTuyaCommand("01", DP_TYPE_BOOL, "01")
    //sendZigbeeCommands( cmds )
    return cmds
}


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
    //if (settings?.logEnable) log.trace "${device.displayName} manufacturer ${manufacturer} group is ${modelGroup}"
    return modelGroup
}



def sendSupportedThermostatModes() {
    def supportedThermostatModes = "[]"
    switch (getModelGroup()) {
        case 'AVATTO' :
        case 'MOES' :
        case 'BEOK' :
        case 'MODEL3' :
            supportedThermostatModes = '[off, heat, auto]'
            break
        case 'BRT-100' :  // BRT-100
            supportedThermostatModes = '[off, heat, auto, emergency heat]'
            //supportedThermostatModes = '[off, heat, auto, emergency heat, eco, test]'
            break
        default :
            supportedThermostatModes = '[off, heat, auto]'
            break
    }    
    sendEvent(name: "supportedThermostatModes", value:  supportedThermostatModes, isStateChange: true)
}

//  called from initialize()
def installed() {
    if (settings?.txtEnable) log.info "installed()"
    
    sendEvent(name: "supportedThermostatFanModes", value: ["auto"], isStateChange: true)    
    sendSupportedThermostatModes()
    sendEvent(name: "thermostatMode", value: "heat", isStateChange: true)
    sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: true)
    state.lastThermostatMode = "heat"
    sendThermostatOperatingStateEvent( "idle" )
    sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true)
    sendEvent(name: "thermostatSetpoint", value:  20, unit: "\u00B0"+"C", isStateChange: true)        // Google Home compatibility
    sendEvent(name: "heatingSetpoint", value: 20, unit: "\u00B0"+"C", isStateChange: true)
    sendEvent(name: "coolingSetpoint", value: 30, unit: "\u00B0"+"C", isStateChange: true)
    sendEvent(name: "temperature", value: 22, unit: "\u00B0"+"C", isStateChange: true)    
    updateDataValue("lastRunningMode", "heat")	

    state.mode = ""
    state.setpoint = 0
    unschedule()
    runEvery1Minute(receiveCheck)    // KK: check
}

def updated() {
    ArrayList<String> cmds = []
    if (modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference", "Auto detect")
    }
    /* unconditional */log.info "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b> modelGroupPreference = <b>${modelGroupPreference}</b> (${getModelGroup()})"
    if (settings?.txtEnable) log.info "Force manual is <b>${forceManual}</b>; Resend failed is <b>${resendFailed}</b>"
    if (settings?.txtEnable) log.info "Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, logsOff)    // turn off debug logging after 24 hours
    }
    else {
        unschedule(logsOff)
    }
    def fncmd
    if (getModelGroup() in ['AVATTO']) {
        fncmd = safeToInt( tempCalibration )
        if (settings?.logEnable) log.trace "${device.displayName} setting tempCalibration to= ${fncmd}"
        cmds += sendTuyaCommand("1B", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))     
        fncmd = safeToInt( hysteresis )
        if (settings?.logEnable) log.trace "${device.displayName} setting hysteresis to= ${fncmd}"
        cmds += sendTuyaCommand("6A", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))     
        fncmd = safeToInt( minTemp )
        if (settings?.logEnable) log.trace "${device.displayName} setting minTemp to= ${fncmd}"
        cmds += sendTuyaCommand("1A", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))     
        fncmd = safeToInt( maxTemp )
        if (settings?.logEnable) log.trace "${device.displayName} setting maxTemp to= ${fncmd}"
        cmds += sendTuyaCommand("13", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        if (settings?.programMode != null) {
            def value = safeToInt( programMode )
            if (settings?.logEnable) log.debug "${device.displayName} setting Program Mode to ${PROGRAM_MODE_NAME(value)} (${programMode})"
            cmds += sendTuyaCommand("68", DP_TYPE_ENUM, zigbee.convertToHexString(value as int, 2))
        }
    }
    else if (getModelGroup() in ['BRT-100']) {
        fncmd = safeToInt( tempCalibration )
        if (settings?.logEnable) log.trace "${device.displayName} setting tempCalibration to= ${fncmd}"
        cmds += sendTuyaCommand("69", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        fncmd = safeToInt( minTemp )
        if (settings?.logEnable) log.trace "${device.displayName} setting minTemp to= ${fncmd}"
        cmds += sendTuyaCommand("6D", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))     
        fncmd = safeToInt( maxTemp )
        if (settings?.logEnable) log.trace "${device.displayName} setting maxTemp to= ${fncmd}"
        cmds += sendTuyaCommand("6C", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))     
    }
    
    /* unconditional */ log.info "${device.displayName} Update finished"
    sendZigbeeCommands( cmds ) 
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
        case 'AVATTO' :
            fncmd = 0
            cmds += sendTuyaCommand("27", DP_TYPE_ENUM, zigbee.convertToHexString(fncmd as int, 8))
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

def checkDriverVersion() {
    if (state.driverVersion != null && driverVersionAndTimeStamp() == state.driverVersion) {
    }
    else {
        if (txtEnable==true) log.debug "${device.displayName} updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
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
        state.driverVersion = driverVersionAndTimeStamp()
    }
    //
    state.old_dp = ""
    state.old_fncmd = ""
    state.mode = ""
    state.setpoint = 0
    state.packetID = 0
    state.heatingSetPointRetry = 0
    state.modeSetRetry = 0
    state.rxCounter = 0
    state.txCounter = 0
    state.duplicateCounter = 0
    //
    if (fullInit == true || state.lastThermostatMode == null) state.lastThermostatMode = "unknown"
    if (fullInit == true || state.lastThermostatOperatingState == null) state.lastThermostatOperatingState = "unknown"
    //
    if (fullInit == true || device.getDataValue("logEnable") == null) device.updateSetting("logEnable", true)
    if (fullInit == true || device.getDataValue("txtEnable") == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || device.getDataValue("forceManual") == null) device.updateSetting("forceManual", false)    
    if (fullInit == true || device.getDataValue("resendFailed") == null) device.updateSetting("resendFailed", false)    
    if (fullInit == true || device.getDataValue("minTemp") == null) device.updateSetting("minTemp", 10)    
    if (fullInit == true || device.getDataValue("maxTemp") == null) device.updateSetting("maxTemp", 28)
    if (fullInit == true || device.getDataValue("tempCalibration") == null) device.updateSetting("tempCalibration", 0)
    if (fullInit == true || device.getDataValue("hysteresis") == null) device.updateSetting("hysteresis", 1)
    //
    
    
}

def configure() {
    initialize()
}

def initialize() {
    if (true) "${device.displayName} Initialize()..."
    unschedule()
    initializeVars()
    setDeviceLimits()
    installed()
    updated()
    runIn( 3, logInitializeRezults)
}

def setDeviceLimits() { // for google and amazon compatability
    sendEvent(name:"minHeatingSetpoint", value: settings.minTemp ?: 10, unit: "°C", isStateChange: true, displayed: false)
	sendEvent(name:"maxHeatingSetpoint", value: settings.maxTemp ?: 28, unit: "°C", isStateChange: true, displayed: false)
    updateDataValue("lastRunningMode", "heat")
	log.trace "setDeviceLimits - device max/min set"
}	

def modeReceiveCheck() {
    if (settings?.resendFailed == false )  return
    
    if (state.mode != "") {
        if (settings?.logEnable) log.warn "${device.displayName} modeReceiveCheck() <b>failed</b>"
        /*
        if (settings?.logEnable) log.debug "${device.displayName} resending mode command :"+state.mode
        def cmds = setThermostatMode(state.mode)
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
        */
    }
    else {
        if (settings?.logEnable) log.debug "${device.displayName} modeReceiveCheck() OK"
    }
}

def setpointReceiveCheck() {
    if (settings?.resendFailed == false )  return

    if (state.setpoint != 0 ) {
        state.heatingSetPointRetry = state.heatingSetPointRetry + 1
        if (state.heatingSetPointRetry < MaxRetries) {
            if (settings?.logEnable) log.warn "${device.displayName} setpointReceiveCheck(${state.setpoint}) <b>failed<b/>"
            if (settings?.logEnable) log.debug "${device.displayName} resending setpoint command :"+state.setpoint
            sendTuyaHeatingSetpoint(state.setpoint)
        }
        else {
            if (settings?.logEnable) log.warn "${device.displayName} setpointReceiveCheck(${state.setpoint}) <b>giving up retrying<b/>"
        }
    }
    else {
        if (settings?.logEnable) log.debug "${device.displayName} setpointReceiveCheck(${state.setpoint}) OK"
    }
}

//  unconditionally scheduled Every1Minute from installed() ..
def receiveCheck() {
    modeReceiveCheck()
    setpointReceiveCheck()
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    if (settings?.logEnable) log.trace "sendTuyaCommand = ${cmds}"
    if (state.txCounter != null) state.txCounter = state.txCounter + 1
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.debug "${device.displayName} sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
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
    log.warn "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def controlMode( mode ) {
    ArrayList<String> cmds = []
    
    switch (mode) {
        case "manual" : 
            cmds += sendTuyaCommand("02", DP_TYPE_ENUM, "00")// + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code
            if (settings?.logEnable) log.trace "${device.displayName} sending manual mode : ${cmds}"
            break
        case "program" :
            cmds += sendTuyaCommand("02", DP_TYPE_ENUM, "01")// + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code
            if (settings?.logEnable) log.trace "${device.displayName} sending program mode : ${cmds}"
            break
        default :
            break
    }
    sendZigbeeCommands( cmds )
}


def childLock( mode ) {
    ArrayList<String> cmds = []
    def dp
    if (getModelGroup() in ["AVATTO"]) dp = "28"
    else if (getModelGroup() in ["BRT-100"]) dp = "0D"
    else return
        
    switch (mode) {
        case "off" : 
            cmds += sendTuyaCommand(dp, DP_TYPE_BOOL, "00")
            break
        case "on" :
            cmds += sendTuyaCommand(dp, DP_TYPE_BOOL, "01")
            break
        default :
            break
    }
    sendEvent(name: "childLock", value: mode)
    if (settings?.logEnable) log.trace "${device.displayName} sending child lock mode : ${mode}"
    sendZigbeeCommands( cmds )    
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
    
    if (settings?.logEnable) log.trace "${device.displayName} sending calibration offset : ${offset}"
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

    switch ( getModelGroup() ) {
        case 'AVATTO' :                          
        case 'MOES' :
        case 'BEOK' :
        case 'MODEL3' :
        case 'BRT-100' :
        case 'TEST2' :                           // MOES
        case 'TEST3' :
        case 'UNKNOWN' :
        default :
            break
    }     

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}    
/*
    BRT-100 Zigbee network re-pair procedure: After the actuator has completed self-test, long press [X] access to interface, short press '+' to choose WiFi icon,
        short press [X] to confirm this option, long press [X]. WiFi icon will start flashing when in pairing mode.

*/
