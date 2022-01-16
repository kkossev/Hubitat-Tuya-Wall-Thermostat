/**
 *  Tuya Wall Thermostat driver for Hubitat Elevation
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
 *
 *                                  TODO: BRT-100 mode receive check fails?
 *                                  TODO: in Initialize do not reset parameters if already exist and are within limits
 *                                  TODO: Check: process TRV Moes BRT-100 Valve position is: 0% (dp=104, fncmd=0) 
 *                                  TODO: handle preset = holiday (Eco mode) for BRT-100
 *                                  TODO: cool command switches AVATTO thermostat off?
 *
*/

def version() { "1.0.5" }
def timeStamp() {"2022/01/16 11:03 AM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition (name: "Tuya Wall Thermostat", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat-Tuya-Wall-Thermostat/main/Tuya-Wall-Thermostat.groovy", singleThreaded: true ) {
        capability "Refresh"
        capability "Sensor"
        capability "Initialize"
		capability "Temperature Measurement"
        capability "Thermostat"
        //capability "ThermostatMode"   
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatSetpoint"
        
        attribute "switch", "enum", ["off", "on"]

        /*
        command "calibration", ["string"]
        command "zTest", [
            [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
            [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
            [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] 
        ]
        */        
        command "initialize"
        command "childLock", [ [name: "ChildLock", type: "ENUM", constraints: ["off", "on"], description: "Select Child Lock mode"] ]        
        
        // (AVATTO)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ye5jkfsb",  deviceJoinName: "AVATTO Wall Thermostat" 
        // (Moes)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_aoclfnxz",  deviceJoinName: "Moes Wall Thermostat" // BHT-002
        // (unknown)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_unknown",  deviceJoinName: "_TZE200_ Thermostat" // unknown
        // (BEOK)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_2ekuz3dz",  deviceJoinName: "Beok Wall Thermostat" // 
        // TEST (BRT-100 for dev tests only!)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_b6wax7g0",  deviceJoinName: "BRT-100 TRV" // BRT-100
        //fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_chyvmhay",  deviceJoinName: "Lidl Silvercrest" // Lidl Silvercrest
        
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: false)
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "forceManual", type: "bool", title: "<b>Force Manual Mode</b>", description: "<i>If the thermostat changes intto schedule mode, then it automatically reverts back to manual mode</i>", defaultValue: false)
        input (name: "resendFailed", type: "bool", title: "<b>Resend failed commands</b>", description: "<i>If the thermostat does not change the Setpoint or Mode as expected, then commands will be resent automatically</i>", defaultValue: false)
        input (name: "minTemp", type: "number", title: "Minimim Temperature", description: "<i>The Minimim temperature that can be sent to the device</i>", defaultValue: 5)
        input (name: "maxTemp", type: "number", title: "Maximum Temperature", description: "<i>The Maximum temperature that can be sent to the device</i>", defaultValue: 28)
        input (name: "modelGroupPreference", title: "Select a model group. Recommended value is <b>'Auto detect'</b>", /*description: "<i>Thermostat type</i>",*/ type: "enum", options:["Auto detect", "AVATTO", "MOES", "BEOK", "MODEL3", "TEST"], defaultValue: "Auto detect", required: false)        
    }
}



@Field static final Map<String, String> Models = [
    '_TZE200_ye5jkfsb'  : 'AVATTO',      // Tuya AVATTO 
    '_TZE200_aoclfnxz'  : 'MOES',        // Tuya Moes BHT series Thermostat BTH-002
    '_TZE200_2ekuz3dz'  : 'BEOK',        // Beok thermostat
    '_TZE200_other'     : 'MODEL3',      // Tuya other models (reserved)
    '_TZE200_b6wax7g0'  : 'TEST',        // BRT-100; ZONNSMART
    '_TZE200_ckud7u2l'  : 'TEST2',       // KKmoon Tuya; temp /10.0
    '_TZE200_zion52ef'  : 'TEST3',       // TRV MOES => fn = "0001 > off:  dp = "0204"  data = "02" // off; heat:  dp = "0204"  data = "01" // on; auto: n/a !; setHeatingSetpoint(preciseDegrees):   fn = "00" SP = preciseDegrees *10; dp = "1002"
    '_TZE200_c88teujp'  : 'TEST3',       // TRV "SEA-TR", "Saswell", model "SEA801" (to be tested)
    '_TZE200_xxxxxxxx'  : 'UNKNOWN',     
    '_TZE200_xxxxxxxx'  : 'UNKNOWN',     
    ''                  : 'UNKNOWN'      // 
]

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
            //log.trace " dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
            state.old_dp = dp
            state.old_fncmd = fncmd
            // the switch cases below default to dp_id = "01"
            switch (dp) {
                case 0x01 :                                                 // 0x01: Heat / Off        DP_IDENTIFIER_THERMOSTAT_MODE_4 0x01 // mode for Moes device used with DP_TYPE_ENUM
                    if (getModelGroup() in ['TEST', 'TEST2']) {
                        processBRT100Presets( dp, fncmd )                       // 0x0401 # Mode (Received value 0:Manual / 1:Holiday / 2:Temporary Manual Mode / 3:Prog)
                    }
                    else {
                        /* version 1.0.4 
                        def mode = (fncmd == 0) ? "off" : "heat"
                        if (settings?.txtEnable) log.info "${device.displayName} Thermostat mode is: ${mode} (dp=${dp}, fncmd=${fncmd})"
                        sendEvent(name: "thermostatMode", value: mode, displayed: true)
                        state.lastThermostatMode = mode
                        if (mode == state.mode) {
                            state.mode = ""
                        }
                        */
                        def mode = (fncmd == 0) ? "off" : "on"
                        if (settings?.txtEnable) log.info "${device.displayName} Thermostat switch is: ${mode} (dp=${dp}, fncmd=${fncmd})"
                        sendEvent(name: "switch", value: mode, displayed: true)
                        if (mode == "off") {
                            sendEvent(name: "thermostatMode", value: mode, displayed: true)
                            sendEvent(name: "thermostatOperatingState", value: "idle", displayed: true)    // do not store as last state!
                        }
                        else {
                            sendEvent(name: "thermostatOperatingState", value: state.lastThermostatOperatingState, displayed: true)    // do not store as last state!
                        }
                        //state.lastThermostatMode = mode
                        //if (mode == state.mode) {
                        //    state.mode = ""
                       // }
                    }
                    break
                case 0x02 : // Mode (LIDL)                                  // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT 0x02 // Heatsetpoint
                    if (getModelGroup() in ['TEST', 'TEST2']) {             // BRT-100 Thermostat heatsetpoint # 0x0202 #
                        processTuyaHeatSetpointReport( fncmd )              // target temp, in degrees (int!)
                        break
                    }
                    else {
                        // DP_IDENTIFIER_THERMOSTAT_MODE_2 0x02 // mode for Moe device used with DP_TYPE_ENUM
                        if (settings?.logEnable) log.trace "device current state = ${device.currentState("switch").value}"
                        if (device.currentState("switch").value == "off") {
                            if (settings?.logEnable) log.warn "ignoring 0x02 command in off mode"
                            break    // ignore 0x02 command if thermostat was switched off !!
                        }
                        else {
                            // continue below.. break statement is missing intentionaly!
                        }
                    }
                case 0x03 : // 0x03 : Scheduled/Manual Mode or // Thermostat current temperature (in decidegrees)
                    if (settings?.logEnable) log.trace "Received dp=0x03: fncmd=${fncmd}"
                    // TODO - use processTuyaModes3( dp, fncmd )
                    if (descMap?.data.size() <= 7) {
                        def mode
                        if (!(fncmd == 0)) {        // KK inverted
                            mode = "auto"    // scheduled
                            //log.trace "forceManual = ${settings?.forceManual}"
                            if (settings?.forceManual == true) {
                                if (settings?.logEnable) log.warn "calling setManualMode()"
                                setManualMode()
                            }
                            else {
                                //log.trace "setManualMode() <b>not called!</b>"
                            }
                        } else {
                            mode = "heat"    // manual
                        }
                        if (settings?.txtEnable) log.info "${device.displayName} Thermostat mode is: $mode (0x${fncmd}) (dp=${dp}, fncmd=${fncmd})"
                        sendEvent(name: "thermostatMode", value: mode, displayed: true)    // mode was confirmed from the Preset info data...
                        state.lastThermostatMode = mode
                    } 
                    else { // # 0x0203 # BRT-100
                        // Thermostat current temperature
                        if (settings?.logEnable) log.trace "processTuyaTemperatureReport descMap?.size() = ${descMap?.data.size()} dp_id=${dp_id} <b>dp=${dp}</b> :"
                        processTuyaTemperatureReport( fncmd )
                    }
                    break
                case 0x04 :                                                 // BRT-100 Boost    DP_IDENTIFIER_THERMOSTAT_BOOST    DP_IDENTIFIER_THERMOSTAT_BOOST 0x04 // Boost for Moes
                    processTuyaBoostModeReport( fncmd )
                    break
                case 0x05 :                                                 // BRT-100 ?
                    if (settings?.txtEnable) log.info "${device.displayName} configuration is done. Result: 0x${fncmd}"
                    break
                case 0x07 :                                                // others Childlock status    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_1 0x07    // 0x0407 > starting moving 
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
                case 0x08 :                                                 // DP_IDENTIFIER_WINDOW_OPEN2 0x08    // BRT-100
                    if (settings?.txtEnable) log.info "${device.displayName} Open window detection MODE (dp=${dp}) is: ${fncmd}"    //0:function disabled / 1:function enabled
                    break
                // case 0x09 : // BRT-100 unknown function
                case 0x0D :                                                 // 0x0D (13) BRT-100 Childlock status    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_4 0x0D MOES, LIDL
                    if (settings?.txtEnable) log.info "${device.displayName} Child Lock (dp=${dp}) is: ${fncmd}"    // 0:function disabled / 1:function enabled
                    break
                case 0x10 :                                                 // 0x10 (16): Heating setpoint
                    // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT_3 0x10         // Heatsetpoint for TRV_MOE mode heat // BRT-100 Rx only?
                    processTuyaHeatSetpointReport( fncmd )
                    break
                case 0x12 :                                                 // 0x12 (18) Max Temp Limit MOES, LIDL
                    // KK TODO - also Window open status (false:true) for TRVs ?    DP_IDENTIFIER_WINDOW_OPEN
                    if (settings?.txtEnable) log.info "${device.displayName} Max Temp Limit is: ${fncmd}"
                    break
                case 0x13 :                                                 // 0x13 (19) Max Temp LIMIT MOES, LIDL
                    if (settings?.txtEnable) log.info "${device.displayName} Max Temp Limit is: ${fncmd}? (dp=${dp}, fncmd=${fncmd})"    // AVATTO - OK
                    break
                case 0x14 :                                                 // 0x14 (20) Dead Zone Temp (hysteresis) MOES, LIDL
                    // KK TODO - also Valve state report : on=1 / off=0 ?  DP_IDENTIFIER_THERMOSTAT_VALVE 0x14 // Valve
                    if (settings?.txtEnable) log.info "${device.displayName} Dead Zone Temp (hysteresis) is: ${fncmd}"
                    break
                case 0x0E :                                                 // 0x0E : BRT-100 Battery # 0x020e # battery percentage (updated every 4 hours )
                case 0x15 :
                    def battery = fncmd >100 ? 100 : fncmd
                    if (settings?.txtEnable) log.info "${device.displayName} battery is: ${fncmd} %"
                    break                
                case 0x18 :                                                 // 0x18(24) : Current (local) temperature
                    if (settings?.logEnable) log.trace "processTuyaTemperatureReport dp_id=${dp_id} <b>dp=${dp}</b> :"
                    processTuyaTemperatureReport( fncmd )
                    break
                case 0x1A :                                                 // AVATTO setpoint lower limit
                    if (settings?.txtEnable) log.info "${device.displayName} Min temperature limit is: ${fncmd}? (dp=${dp}, fncmd=${fncmd})"
                    // TODO - update the minTemp preference !
                    break
                case 0x1B :                                                 // temperature calibration (offset in degree) for Moes (calibration)  // Calibration offset used by AVATO and Moes and Saswell
                    processTuyaCalibration( dp, fncmd )
                    break
                case 0x23 :                                                // 0x23(35) LIDL BatteryVoltage
                    if (settings?.txtEnable) log.info "${device.displayName} BatteryVoltage is: ${fncmd}"
                    break
                case 0x24 :                                                 // 0x24(36) : current (running) operating state (valve)
                    if (settings?.txtEnable) log.info "${device.displayName} thermostatOperatingState is: ${fncmd ? "idle" : "heating"} (dp=${dp}, fncmd=${fncmd})"
                    sendThermostatOperatingStateEvent(fncmd ? "idle" : "heating")
                    //sendEvent(name: "thermostatOperatingState", value: (fncmd ? "idle" : "heating"), displayed: true)
                    break
                case 0x1E :                                                 // DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_3 0x1E // For Moes device
                //case  0x27 :                                                // AVATTO - RESET?
                case 0x28 :                                                 // 0x28(40) KK Child Lock    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_2 0x28
                    if (settings?.txtEnable) log.info "${device.displayName} Child Lock is: ${fncmd}"
                    break
                case 0x2B :                                                 // 0x2B(43) AVATTO Sensor 0-In 1-Out 2-Both    // KK TODO
                    if (settings?.txtEnable) log.info "${device.displayName} Sensor is: ${fncmd==0?'In':fncmd==1?'Out':fncmd==2?'In and Out':'UNKNOWN'} (${fncmd})"
                    break
                case 0x2C :                                                 // temperature calibration (offset in degree)   //DP_IDENTIFIER_THERMOSTAT_CALIBRATION_2 0x2C // Calibration offset used by others
                    processTuyaCalibration( dp, fncmd )
                    break
                // case 0x2D :        // 0x2D(45) LIDL ErrorStatus
                // case 0x62 : // DP_IDENTIFIER_REPORTING_TIME 0x62 (Sensors)
                case 0x65 :                                                 // 0x65(101) AVATTO PID ; also LIDL ComfortTemp
                    if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} Thermostat PID regulation point is: ${fncmd}"    // Model#1 only !!
                    }
                    else {
                        if (settings?.logEnable) log.info "${device.displayName} Thermostat SCHEDULE_1 data received (not processed)..."
                    }
                    break
                case 0x66 :                                                 // 0x66(102) min temperature limit; also LIDL EcoTemp
                    if (settings?.txtEnable) log.info "${device.displayName} Min temperature limit is: ${fncmd}"
                    break
                case 0x67 :                                                 // 0x67(103) max temperature limit; also LIDL AwaySetting
                    if (getModelGroup() in ['TEST']) {                      // #0x0267 # Boost heating countdown in second (Received value [0, 0, 1, 44] for 300)
                        if (settings?.txtEnable) log.info "${device.displayName} Boost heating countdown: ${fncmd} seconds"
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} unknown parameter is: ${fncmd} (dp=${dp}, fncmd=${fncmd}, data=${descMap?.data})"
                    }
                    // KK TODO - could be setpoint for some devices ?
                    // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT_2 0x67 // Heatsetpoint for Moe ?
                    break
                case 0x68 :                                                 // 0x68 (104) DP_IDENTIFIER_THERMOSTAT_VALVE_2 0x68 // Valve; also LIDL TempCalibration!
                    if (getModelGroup() in ['AVATTO']) {
                        if (settings?.txtEnable) log.info "${device.displayName} Dead Zone temp (hysteresis) is: ${fncmd}? (dp=${dp}, fncmd=${fncmd})"
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} Valve position is: ${fncmd}% (dp=${dp}, fncmd=${fncmd})"
                    }
                    // # 0x0268 # TODO - send event! (works OK with BRT-100 (values of 25 / 50 / 75 / 100) 
                    break
                case 0x69 :                                                 // 0x69 (105) BRT-100 temp calibration // could be also Heatsetpoint for TRV_MOE mode auto ? also LIDL
                    if (getModelGroup() in ['TEST']) {
                        processTuyaCalibration( dp, fncmd )
                    }
                    else {
                         log.warn "${device.displayName} (DP=0x69) ?TRV_MOE auto mode Heatsetpoint? value is: ${fncmd}"
                    }
                    break
                case 0x6A : // DP_IDENTIFIER_THERMOSTAT_MODE_1 0x6A // mode used with DP_TYPE_ENUM    Energy saving mode (Received value 0:off / 1:on)
                    if (settings?.txtEnable) log.info "${device.displayName} Energy saving mode? (dp=${dp}) is: ${fncmd} data = ${descMap?.data})"    // 0:function disabled / 1:function enabled
                    break
                case 0x6B :                                                 // DP_IDENTIFIER_TEMPERATURE 0x6B (Sensors)      // BRT-100 !
                    if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6B) Energy saving mode temperature value is: ${fncmd}"    // for BRT-100 # 0x026b # Energy saving mode temperature ( Received value [0, 0, 0, 15] )
                    break
                case 0x6C :                                                 // DP_IDENTIFIER_HUMIDITY 0x6C  (Sensors)
                    if (getModelGroup() in ['TEST']) {  
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6C) Max target temp is: ${fncmd}"        // BRT-100 ( Received value [0, 0, 0, 35] )
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6C) humidity value is: ${fncmd}"
                    }
                    break
                case 0x6D :                                                 // Valve position in % (also // DP_IDENTIFIER_THERMOSTAT_SCHEDULE_4 0x6D // Not finished)
                    if (getModelGroup() in ['TEST']) {                      // 0x026d # Min target temp (Received value [0, 0, 0, 5])
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6D) Min target temp is: ${fncmd}"
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6D) valve position is: ${fncmd} (dp=${dp}, fncmd=${fncmd})"
                    }
                    // KK TODO if (valve > 3) => On !
                    break
                case 0x6E :                                                 // Low battery    DP_IDENTIFIER_BATTERY 0x6E
                    if (settings?.txtEnable) log.info "${device.displayName} Battery (DP= 0x6E) is: ${fncmd}"
                    break
                case 0x70 :                                                 // Reporting    DP_IDENTIFIER_REPORTING 0x70
                    // DP_IDENTIFIER_THERMOSTAT_SCHEDULE_2 0x70 // work days (6)
                    if (settings?.txtEnable) log.info "${device.displayName} reporting status state : ${descMap?.data}"
                    break
                //case 0x71 :// DP_IDENTIFIER_THERMOSTAT_SCHEDULE_3 0x71 // holiday = Not working day (6)
                // case 0x74 :  // 0x74(116)- LIDL OpenwindowTemp
                // case 0x75 :  // 0x75(117) - LIDL OpenwindowTime
                case 0x2D : // KK Tuya cmd: dp=45 value=0 descMap.data = [00, 08, 2D, 05, 00, 01, 00]
                case 0x6C : // KK Tuya cmd: dp=108 value=404095046 descMap.data = [00, 08, 6C, 00, 00, 18, 06, 00, 28, 08, 00, 1C, 0B, 1E, 32, 0C, 1E, 32, 11, 00, 18, 16, 00, 46, 08, 00, 50, 17, 00, 3C]
                default :
                    if (settings?.logEnable) log.warn "${device.displayName} NOT PROCESSED Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                    break
            } //  (dp) switch
        } else {
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
        case 'TEST' :
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
    if (settings?.txtEnable) log.info "${device.displayName} heatingSetpoint is: ${setpointValue}"
    sendEvent(name: "heatingSetpoint", value: setpointValue as int, unit: "C", displayed: true)
    sendEvent(name: "thermostatSetpoint", value: setpointValue as int, unit: "C", displayed: false)        // Google Home compatibility
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
        case 'TEST' :                                  // BRT-100
        case 'TEST2' :
        case 'TEST3' :
            currentTemperatureValue = fncmd / 10.0
            break
        case 'UNKNOWN' :
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
    if (settings?.txtEnable) log.info "${device.displayName} temperature is: ${currentTemperatureValue}"
    sendEvent(name: "temperature", value: currentTemperatureValue, unit: "C", displayed: true)
}

def processTuyaCalibration( dp, fncmd )
{
    def temp = fncmd 
    if (getModelGroup() in ['AVATTO', 'TEST'] ){    // (dp=27, fncmd=-1)
    }
    else {    // "_TZE200_aoclfnxz"
        if (temp > 2048) {
            temp = temp - 4096;
        }
        temp = temp / 100        // KK - check !
    }
    if (settings?.txtEnable) log.info "${device.displayName} temperature calibration (correction) is: ${temp} ? (dp=${dp}, fncmd=${fncmd}) "
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
    if (getModelGroup() in ['TEST', 'TEST2']) {    // BRT-100 ?    KK: TODO!
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
            else if (model in ['TEST']) {    // BRT-100: off mode is also reffered as 'holiday' or 'away'
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
                if (device.currentState("switch").value == "off") {
                    cmds += switchThermostatOn()
                }
                dp = "02"    // was "01" 
                fn = "00"    // was "01" 
            }
            else if (model in ['TEST']) {    // BRT-100
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
                if (device.currentState("switch").value == "off") {
                    cmds += switchThermostatOn()
                }
                dp = "02"    // was "01"
                fn = "01"     // was "02"
                //                return sendTuyaCommand(dp, DP_TYPE_ENUM, fn)    
            }
            else if (model in ['TEST']) {    // BRT-100
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
            if (model in ['TEST']) {    // BRT-100
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
    //return sendTuyaCommand(dp, DP_TYPE_BOOL, fn)
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
        case 'TEST' :                            // BRT-100
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
    
    state.heatingSetPointRetry = 0
    sendTuyaHeatingSetpoint( temperature )
}


def setCoolingSetpoint(temperature){
    if (settings?.logEnable) log.debug "${device.displayName} setCoolingSetpoint(${temperature}) called!"
    if (temperature != (temperature as int)) {
        temperature = (temperature + 0.5 ) as int
        if (settings?.logEnable) log.trace "corrected temperature: ${temperature}"
    }
    sendEvent(name: "coolingSetpoint", value: temperature, unit: "C", displayed: false)
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
    def modelGroup = 'Unknown'
    if (modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference", "Auto detect")
    }
    if (modelGroupPreference == "Auto detect") {
        if (manufacturer in Models) {
            modelGroup = Models[manufacturer]
        }
        else {
             modelGroup = 'Unknown'
        }
    }
    else {
         modelGroup = modelGroupPreference 
    }
    //    log.trace "manufacturer ${manufacturer} group is ${modelGroup}"
    return modelGroup
}


def sendSupportedThermostatModes() {
    def supportedThermostatModes = "[]"
    switch (getModelGroup()) {
        case 'AVATTO' :
            supportedThermostatModes = '[off, heat, auto]'
            break
        case 'MOES' :
        case 'BEOK' :
        case 'MODEL3' :
            supportedThermostatModes = '[off, heat]'
            break
        case 'TEST' :  // BRT-100
            supportedThermostatModes = '[off, heat, auto, emergency heat]'
            //supportedThermostatModes = '[off, heat, auto, emergency heat, eco, test]'
            break
        default :
            supportedThermostatModes = '[heat, auto]'
            break
    }    
    sendEvent(name: "supportedThermostatModes", value:  supportedThermostatModes, isStateChange: true, displayed: true)
}

//  called from initialize()
def installed() {
    if (settings?.txtEnable) log.info "installed()"
    sendSupportedThermostatModes()
    sendEvent(name: "supportedThermostatFanModes", value: ["auto"])    
    sendEvent(name: "thermostatMode", value: "heat", displayed: false)
    state.lastThermostatMode = "heat"
    sendThermostatOperatingStateEvent( "idle" )
    //sendEvent(name: "thermostatOperatingState", value: "idle", displayed: false)
    sendEvent(name: "heatingSetpoint", value: 20, unit: "C", displayed: false)
    sendEvent(name: "coolingSetpoint", value: 20, unit: "C", displayed: false)
    sendEvent(name: "temperature", value: 20, unit: "C", displayed: false)     
    sendEvent(name: "thermostatSetpoint", value:  20, unit: "C", displayed: false)        // Google Home compatibility
    sendEvent(name: "switch", value: "on", displayed: true)

    state.mode = ""
    state.setpoint = 0
    unschedule()
    runEvery1Minute(receiveCheck)    // KK: check
}

def updated() {
    if (modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference", "Auto detect")
    }
    /* unconditional */log.info "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b> modelGroupPreference = <b>${modelGroupPreference}</b> (${getModelGroup()})"
    if (settings?.txtEnable) log.info "Force manual is <b>${forceManual}</b>; Resend failed is <b>${resendFailed}</b>"
    if (settings?.txtEnable) log.info "Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(1800, logsOff)    // turn off debug logging after 30 minutes
    }
    else {
        unschedule(logsOff)
    }
    /* unconditional */ log.info "Update finished"
}

def refresh() {
    if (settings?.logEnable)  {log.debug "${device.displayName} refresh()..."}
    zigbee.readAttribute(0 , 0 )
}

def logInitializeRezults() {
    log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")} ModelGroup = ${getModelGroup()}"
    log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// called by initialize() button
void initializeVars() {
    if (settings?.logEnable) log.debug "${device.displayName} UnitializeVars()..."
    state.clear()
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
    state.lastThermostatMode = "unknown"
    state.lastThermostatOperatingState = "unknown"
    //
    device.updateSetting("logEnable", false)    
    device.updateSetting("txtEnable", true)    
    device.updateSetting("forceManual", false)    
    device.updateSetting("resendFailed", false)    
    device.updateSetting("minTemp", 5)    
    device.updateSetting("maxTemp", 28)    

}



def configure() {
    initialize()
}

def initialize() {
    if (true) "${device.displayName} Initialize()..."
    // sendEvent(name: "supportedThermostatModes", value: ["off", "cool"])
    unschedule()
    initializeVars()
    installed()
    updated()
    runIn( 3, logInitializeRezults)
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
    else if (getModelGroup() in ["TEST"]) dp = "0D"
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
    if (settings?.logEnable) log.trace "${device.displayName} sending child lock mode : ${mode}"
    sendZigbeeCommands( cmds )    
}

def calibration( offset ) {
    offset = 0
    ArrayList<String> cmds = []
    def dp
    if (getModelGroup() in ["AVATTO"]) dp = "1B"
    else if (getModelGroup() in ["TEST"]) dp = "69"
    else return;
     // callibration command returns also thermostat mode (heat), operation mode (manual), heating stetpoint and few seconds latrer - the temperature!
    cmds += sendTuyaCommand(dp, DP_TYPE_VALUE, zigbee.convertToHexString(offset as int, 8))
    
    if (settings?.logEnable) log.trace "${device.displayName} sending calibration offset : ${offset}"
    sendZigbeeCommands( cmds )    
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
        case 'TEST' :                            // BRT-100
        case 'TEST2' :                           // MOES
        case 'TEST3' :
        case 'UNKNOWN' :
        default :
            break
    }     

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}    
