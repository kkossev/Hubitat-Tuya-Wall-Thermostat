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
 * ver. 1.0.0 2022-01-06 kkossev  - Inital version ( development branch! )
 *
*/
import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol

def version() { "1.0.0" }
def timeStamp() {"2022/01/08 11:36 PM"}

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
        

        command "initialize"
        command "operationMode", [ [name: "Mode", type: "ENUM", constraints: ["manual", "program"], description: "Select thermostat mode"] ]        
        
        // Model#1 (AVATTO)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ye5jkfsb",  deviceJoinName: "AVATTO Wall Thermostat" 
        // Model#2 (Moes)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_aoclfnxz",  deviceJoinName: "Moes Wall Thermostat" // BHT-002
        // Model#3 (unknown)
        // Model#4 (BRT-100 for dev tests only!)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_b6wax7g0",  deviceJoinName: "BRT-100 TRV" // BRT-100
        
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: false)
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "forceManual", type: "bool", title: "<b>Force Manual Mode</b>", description: "<i>If the thermostat changes intto schedule mode, then it automatically reverts back to manual mode</>", defaultValue: false)
        input (name: "modelGroup", title: "Model group", description: "<i>Thermostat type</i>", type: "enum", options:["Auto detect", "AVATTO", "MOES", "MODEL3", "TEST"], defaultValue: "Auto detect", required: false)        
        input (name: "resendFailed", type: "bool", title: "<b>Resend failed commands</b>", description: "<i>If the thermostat does not change the Setpoint or Mode as expected, then commands will be resent automatically</i>", defaultValue: false)
    }
}



@Field static final Map<String, String> Models = [
    '_TZE200_ye5jkfsb'  : 'AVATTO',      // Tuya AVATTO 
    '_TZE200_aoclfnxz'  : 'MOES',        // Tuya Moes BHT series
    '_TZE200_other'     : 'MODEL3',      // Tuya other models (reserved)
    '_TZE200_b6wax7g0'  : 'TEST',        // BRT-100; ZONNSMART
    '_TZE200_ckud7u2l'  : 'TEST2',       // KKmoon Tuya; temp /10.0
    ''                  : 'UNKNOWN'      // 
]
                                
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
    if (settings?.logEnable) log.debug "${device.displayName} parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
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
                return
            }
            //log.trace " dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
            state.old_dp = dp
            state.old_fncmd = fncmd
            // the switch cases below default to dp_id = "01"
            switch (dp) {
                case 0x01 :                                                 // 0x01: Heat / Off        DP_IDENTIFIER_THERMOSTAT_MODE_4 0x01 // mode for Moes device used with DP_TYPE_ENUM
                    if (getModelGroup() == 'TEST') {
                    //if (device.getDataValue("manufacturer") == "_TZE200_b6wax7g0") {
                        processBRT100Presets( fncmd )
                    }
                    else {
                        def mode = (fncmd == 0) ? "off" : "heat"
                        if (settings?.txtEnable) log.info "${device.displayName} Thermostat mode is: ${mode}"
                        sendEvent(name: "thermostatMode", value: mode, displayed: true)
                        if (mode == state.mode) {
                            state.mode = ""
                        }
                    }
                    break
                case 0x02 :                                                 // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT 0x02 // Heatsetpoint
                    if (getModelGroup() in ['TEST', 'TEST2']) {             // BRT-100 Thermostat heatsetpoint
                        processTuyaHeatSetpointReport( fncmd )
                        break
                    }
                    else {
                        // DP_IDENTIFIER_THERMOSTAT_MODE_2 0x02 // mode for Moe device used with DP_TYPE_ENUM
                        // continue below..
                    }
                case 0x03 : // 0x03 : Scheduled/Manual Mode
                    // TODO - use processTuyaModes3( dp, fncmd )
                    if (descMap?.data.size() <= 7) {
                        def controlMode
                        if (!(fncmd == 0)) {        // KK inverted
                            controlMode = "scheduled"
                            //log.trace "forceManual = ${settings?.forceManual}"
                            if (settings?.forceManual == true) {
                                if (settings?.logEnable) log.warn "calling setManualMode()"
                                setManualMode()
                            }
                            else {
                                //log.trace "setManualMode() <b>not called!</b>"
                            }
                        } else {
                            controlMode = "manual"
                        }
                        if (settings?.txtEnable) log.info "${device.displayName} Thermostat mode is: $controlMode (0x${fncmd})"
                        // TODO - add event !!!
                    }
                    else {
                        // Thermostat current temperature
                        log.trace "processTuyaTemperatureReport descMap?.size() = ${descMap?.data.size()} dp_id=${dp_id} <b>dp=${dp}</b> :"
                        processTuyaTemperatureReport( fncmd )
                    }
                    break
                case 0x04 :                                                 // BRT-100 Boost    DP_IDENTIFIER_THERMOSTAT_BOOST    DP_IDENTIFIER_THERMOSTAT_BOOST 0x04 // Boost for Moes
                    def boostMode = fncmd == 0 ? "off" : "on"                // "manual" : "boost"
                    if (settings?.txtEnable) log.info "${device.displayName} Boost mode is: $boostMode (0x${fncmd})"
                    // TODO - verify and use processTuyaModes4( dp, fncmd )
                    break
                case 0x05 :                                                 // BRT-100 ?
                    if (settings?.txtEnable) log.info "${device.displayName} configuration is done. Result: 0x${fncmd}"
                    break
                // case 0x09 : // BRT-100 ?
                case 0x07 :                                                 // others Childlock status    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_1 0x07    // 0x0407 > starting moving 
                // case 0x08 : DP_IDENTIFIER_WINDOW_OPEN2 0x08
                case 0x0D :                                                 // BRT-100 Childlock status    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_4 0x0D
                    if (settings?.txtEnable) log.info "${device.displayName} Child Lock (dp=${dp}) is: ${fncmd}"
                    break
                case 0x10 :                                                 // 0x10: Target Temperature / heating setpoint
                    // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT_3 0x10         // Heatsetpoint for TRV_MOE mode heat
                    processTuyaHeatSetpointReport( fncmd )
                    break
                case 0x12 :                                                 // Max Temp Limit
                    // KK TODO - also Window open status (false:true) for TRVs ?    DP_IDENTIFIER_WINDOW_OPEN
                    if (settings?.txtEnable) log.info "${device.displayName} Max Temp Limit is: ${fncmd}"
                    break
                case 0x13 :                                                 // Max Temp 
                    if (settings?.txtEnable) log.info "${device.displayName} Max Temp is: ${fncmd}"
                    break
                case 0x14 :                                                 // Dead Zone Temp (hysteresis)
                    // KK TODO - also Valve state report : on=1 / off=0 ?  DP_IDENTIFIER_THERMOSTAT_VALVE 0x14 // Valve
                    if (settings?.txtEnable) log.info "${device.displayName} Dead Zone Temp (hysteresis) is: ${fncmd}"
                    break
                case 0x0E :                                                 // BRT-100 Battery
                case 0x15 :
                    def battery = fncmd >100 ? 100 : fncmd
                    if (settings?.txtEnable) log.info "${device.displayName} battery is: ${fncmd} %"
                    break                
                case 0x18 :                                                 // 0x18 : Current (local) temperature
                    log.trace "processTuyaTemperatureReport dp_id=${dp_id} <b>dp=${dp}</b> :"
                    processTuyaTemperatureReport( fncmd )
                    break
                case 0x1B :                                                 // temperature calibration (offset in degree) for Moes (calibration)  // DP_IDENTIFIER_THERMOSTAT_CALIBRATION_1 0x1B // Calibration offset used by Moes and Saswell
                    processTuyaCalibration( fncmd )
                    break                
                case 0x24 :                                                 // 0x24 : current (running) operating state (valve)
                    if (settings?.txtEnable) log.info "${device.displayName} thermostatOperatingState is: ${fncmd ? "idle" : "heating"}"
                    sendEvent(name: "thermostatOperatingState", value: (fncmd ? "idle" : "heating"), displayed: true)
                    break
                case 0x1E :                                                 // DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_3 0x1E // For Moes device
                case 0x28 :                                                 // KK Child Lock    DP_IDENTIFIER_THERMOSTAT_CHILDLOCK_2 0x28
                    if (settings?.txtEnable) log.info "${device.displayName} Child Lock is: ${fncmd}"
                    break
                case 0x2B :                                                 // KK Sensor?
                    if (settings?.txtEnable) log.info "${device.displayName} Sensor is: ${fncmd}"
                    break
                case 0x2C :                                                 // temperature calibration (offset in degree)   //DP_IDENTIFIER_THERMOSTAT_CALIBRATION_2 0x2C // Calibration offset used by others
                    processTuyaCalibration( fncmd * 10)
                    break
                // case 0x62 : // DP_IDENTIFIER_REPORTING_TIME 0x62 (Sensors)
                case 0x65 :                                                 // AVATTO PID 
                    // KK also DP_IDENTIFIER_THERMOSTAT_SCHEDULE_1 0x65 // Moe thermostat W124 (4) + W002 (4) + W001 (4)
                    if (settings?.txtEnable) log.info "${device.displayName} Thermostat PID regulation point is: ${fncmd}"    // Model#1 only !!
                    // TODO - filter for BRT-100 !!!!!!!!!!!!!!! DP_IDENTIFIER_THERMOSTAT_MODE_3 0x65 // mode for Saswell device used with DP_TYPE_BOOL
                    break
                case 0x66 :                                                 // min temperature limit
                    if (settings?.txtEnable) log.info "${device.displayName} Min temperature limit is: ${fncmd}"
                    break
                case 0x67 :                                                 // max temperature limit
                    if (settings?.txtEnable) log.info "${device.displayName} Max temperature limit is: ${fncmd}"
                    // KK TODO - could be setpoint for some devices ?
                    // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT_2 0x67 // Heatsetpoint for Moe
                    break
                case 0x68 :                                                 // DP_IDENTIFIER_THERMOSTAT_VALVE_2 0x68 // Valve
                    if (settings?.txtEnable) log.info "${device.displayName} Valve position is: ${fncmd} %"
                    // TODO - send event! (works OK with BRT-100 )
                    break
                case 0x69 :                                                 // DP_IDENTIFIER_THERMOSTAT_HEATSETPOINT_4 0x69 // Heatsetpoint for TRV_MOE mode auto ?
                    // TODO if (productId == "Tuya_THD MOES TRV")..
                    if (settings?.txtEnable) log.info "${device.displayName} (DP=0x69) value is: ${fncmd}"
                    break
                // case 0x6A : // DP_IDENTIFIER_THERMOSTAT_MODE_1 0x6A // mode used with DP_TYPE_ENUM
                case 0x6B :                                                 // DP_IDENTIFIER_TEMPERATURE 0x6B (Sensors)
                    if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6B) temperature value is: ${fncmd}"
                    break
                case 0x6C :                                                 // DP_IDENTIFIER_HUMIDITY 0x6C  (Sensors)
                    if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6C) humidity value is: ${fncmd}"
                    break
                case 0x6D :                                                 // Valve position in % (also // DP_IDENTIFIER_THERMOSTAT_SCHEDULE_4 0x6D // Not finished)
                    if (settings?.txtEnable) log.info "${device.displayName} (DP=0x6D) valve position is: ${fncmd}"
                    // TODO if (valve > 3) => On !
                    break
                case 0x6E :                                                 // Low battery    DP_IDENTIFIER_BATTERY 0x6E
                    if (settings?.txtEnable) log.info "${device.displayName} Battery (DP= 0x6E) is: ${fncmd}"
                    break
                case 0x70 :                                                 // Reporting    DP_IDENTIFIER_REPORTING 0x70
                    // DP_IDENTIFIER_THERMOSTAT_SCHEDULE_2 0x70 // work days (6)
                    if (settings?.txtEnable) log.info "${device.displayName} reporting status state : ${descMap?.data}"
                    break
                //case 0x71 :// DP_IDENTIFIER_THERMOSTAT_SCHEDULE_3 0x71 // holiday = Not working day (6)
                // unprocessed -> default :
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

boolean useTuyaCluster( manufacturer )
{
    // https://docs.tuya.com/en/iot/device-development/module/zigbee-module/zigbeetyzs11module?id=K989rik5nkhez
    //_TZ3000 don't use tuya cluster
    //_TYZB01 don't use tuya cluster
    //_TYZB02 don't use tuya cluster
    //_TZ3400 don't use tuya cluster

    if (manufacturer.startsWith("_TZE200_") || // Tuya cluster visible
        manufacturer.startsWith("_TYST11_"))   // Tuya cluster invisible
    {
        return true;
    }
    return false;
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
            setpointValue = fncmd    // or ?
            break
        case 'MODEL3' :
            setpointValue = fncmd    // or * 100 / 2 ?
            break
        case 'TEST' :
            setpointValue = fncmd
            break
        case 'TEST2' :
            setpointValue = fncmd / 10.0
            break
        default :
            setpointValue = fncmd
            break
    }
    if (settings?.txtEnable) log.info "${device.displayName} heatingSetpoint is: ${setpointValue}"
    sendEvent(name: "heatingSetpoint", value: setpointValue as int, unit: "C", displayed: true)
    // sendEvent(name: "coolingSetpoint", value: setpointValue as int, unit: "C", displayed: false)
    sendEvent(name: "thermostatSetpoint", value: setpointValue as int, unit: "C", displayed: false)        // Google Home compatibility
    if (setpointValue == state.setpoint)  {
        state.setpoint = 0
    }                        
}                        

def processTuyaTemperatureReport( fncmd )
{
    def currentTemperatureValue
    def model = getModelGroup()
    switch (model) {
        case 'AVATTO' :
            currentTemperatureValue = fncmd
            break
        case 'MOES' :
        case 'TEST2' :
            currentTemperatureValue = fncmd / 10.0
            break
        case 'MODEL3' :
            currentTemperatureValue = fncmd    // or * 100 / 2 ?
            break
        case 'TEST' :
            currentTemperatureValue = fncmd
            break
        default :
            currentTemperatureValue = fncmd
            break
    }    
    if (settings?.txtEnable) log.info "${device.displayName} temperature is: ${currentTemperatureValue}"
    sendEvent(name: "temperature", value: currentTemperatureValue, unit: "C", displayed: true)
}

def processTuyaCalibration( fncmd )
{
    def temp = fncmd
    if ( true /*device.getDataValue("manufacturer") == "_TZE200_aoclfnxz"*/) { // Only Model#2 Moes?
        if (temp > 2048) {
            temp = temp - 4096;
        }
    }
    temp = temp * 100;    
    if (settings?.txtEnable) log.info "${device.displayName} temperature calibration (correction) is: ${temp}"
}

def processBRT100Presets( data ) {
    def mode
    def preset
    if (data == 0) { //programming
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
        mode = "auto"
        preset = "holiday"
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} processBRT100Presets unknown: ${data}"
        return;
    }
    // TODO - change thremostatMode depending on mode ?
    // TODO - change tehrmostatPreset depending on preset ?
    if (settings?.txtEnable) log.info "${device.displayName} BRT-100 Presets: mode = ${mode} preset = ${preset}"
}

def processTuyaModes3( dp, data ) {
    // dp = 0x0402 : // preset for moes or mode
    // dp = 0x0403 : // preset for moes    
    if (device.getDataValue("manufacturer") == "_TZE200_b6wax7g0") {    // BRT-100 ?    
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
    else {
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

def setThermostatMode(mode){
    if (settings?.logEnable) log.debug "${device.displayName} setThermostatMode(${mode})"
    switch (mode) {
        case "auto" :
        case "heat" :
        case "emergency heat" :
            state.mode = "heat"
            break
        case "off" :
        case "cool" :
            state.mode = "off"
            break
        default :
            log.warn "Unsupported mode ${mode}"
            return
    }
    runIn(4, modeReceiveCheck/*, [overwrite:true]*/)    // KK check!
    sendTuyaCommand("01", DP_TYPE_BOOL, state.mode =="heat" ? "01" : "00")
}

def setHeatingSetpoint(temperature){
    if (settings?.logEnable) log.debug "${device.displayName} setHeatingSetpoint(${temperature})"
    def settemp = temperature as int 
    settemp += (settemp != temperature && temperature > device.currentValue("heatingSetpoint")) ? 1 : 0
    if (settings?.logEnable) log.debug "${device.displayName} change setpoint to ${settemp}"
    state.setpoint = settemp
    runIn(4, setpointReceiveCheck/*, [overwrite:true]*/)      // KK check!
    sendTuyaCommand("10", DP_TYPE_VALUE, zigbee.convertToHexString(settemp as int, 8))
}

def setCoolingSetpoint(temperature){
    if (settings?.logEnable) log.debug "${device.displayName} setCoolingSetpoint(${temperature}) called!"
    setHeatingSetpoint(temperature)
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

def auto() { setThermostatMode("heat") }
def emergencyHeat() { setThermostatMode("heat") }
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


def getModelGroup() {
    def manufacturer = device.getDataValue("manufacturer")
    def modelGroup
    if (manufacturer in Models) {
        modelGroup = Models[manufacturer]
    }
    else {
         modelGroup = 'Unknown'
    }
        log.trace "manufacturer ${manufacturer} group is ${modelGroup}"
    return modelGroup
}

def installed() {
    if (settings?.txtEnable) log.info "installed()"
    sendEvent(name: "supportedThermostatModes", value:  ["off", "heat"], isStateChange: true, displayed: true)
    sendEvent(name: "supportedThermostatFanModes", value: ["auto"])    
    sendEvent(name: "thermostatMode", value: "heat", displayed: false)
    sendEvent(name: "thermostatOperatingState", value: "idle", displayed: false)
    sendEvent(name: "heatingSetpoint", value: 20, unit: "C", displayed: false)
    sendEvent(name: "coolingSetpoint", value: 20, unit: "C", displayed: false)
    sendEvent(name: "temperature", value: 20, unit: "C", displayed: false)     
    sendEvent(name: "thermostatSetpoint", value:  20, unit: "C", displayed: false)        // Google Home compatibility

    state.mode = ""
    state.setpoint = 0
    unschedule()
    runEvery1Minute(receiveCheck)    // KK: check
}

def updated() {
    if (settings?.txtEnable) log.info "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b> ModelGroup = ${getModelGroup()}"
    if (settings?.txtEnable) log.info "Force manual is <b>${forceManual}</b>; Resend failed is <b>${resendFailed}</b>"
    if (settings?.txtEnable) log.info "Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(1800, logsOff)    // turn off debug logging after 30 minutes
    }
    else {
        unschedule(logsOff)
    }
    if (settings?.txtEnable) log.info "Update finished"
}

def refresh() {
    if (settings?.logEnable)  {log.debug "${device.displayName} refresh()..."}
    zigbee.readAttribute(0 , 0 )
}

def logInitializeRezults() {
    log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")} ModelGroup = ${getModelGroup()}"
    log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp:${timeStamp()})"
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
    //
    device.updateSetting("logEnable", true)    
    device.updateSetting("txtEnable", true)    
    device.updateSetting("forceManual", false)    
    device.updateSetting("resendFailed", false)    

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
    
    if (settings?.logEnable) log.debug "${device.displayName} modeReceiveCheck()"
    if (state.mode != "") {
        if (settings?.logEnable) log.debug "${device.displayName} resending mode command :"+state.mode
        def cmds = setThermostatMode(state.mode)
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
    }
}

def setpointReceiveCheck() {
    if (settings?.resendFailed == false )  return

    if (settings?.logEnable) log.debug "${device.displayName} setpointReceiveCheck()"
    if (state.setpoint != 0 ) {
        if (settings?.logEnable) log.debug "${device.displayName} resending setpoint command :"+state.setpoint
        def cmds = setHeatingSetpoint(state.setpoint)
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
    }
}

def receiveCheck() {
    modeReceiveCheck()
    setpointReceiveCheck()
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    if (settings?.logEnable) log.trace "sendTuyaCommand = ${cmds}"
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.debug "${device.displayName} sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
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

def operationMode( mode ) {
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



