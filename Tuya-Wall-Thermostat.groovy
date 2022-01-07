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
def timeStamp() {"2022/01/07 12:42 AM"}

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
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ye5jkfsb",  deviceJoinName: "Tuya Wall Thermostat" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_aoclfnxz",  deviceJoinName: "Moes Wall Thermostat" // BHT-002
        
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: false)
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "forceManual", type: "bool", title: "<b>Force Manual Mode</b>", description: "<i>If the thermostat changes intto schedule mode, then it automatically reverts back to manual mode</>", defaultValue: false)
        input (name: "modelGroup", title: "Model group", description: "<i>Thermostat type</i>", type: "enum", options:["Auto detect", "Model 1", "Model 2", "Model 3"], defaultValue: "Auto detect", required: true)        
        input (name: "resendFailed", type: "bool", title: "<b>Resend failed commands</b>", description: "<i>If the thermostat does not change the Setpoint or Mode as expected, then commands will be resent automatically</i>", defaultValue: false)
    }
}


private getCLUSTER_TUYA() { 0xEF00 }
private getSETDATA() { 0x00 }
private getSETTIME() { 0x24 }

// tuya DP type
private getDP_TYPE_RAW()     { "01" }    // [ bytes ]
private getDP_TYPE_BOOL()    { "01" }    // [ 0/1 ]
private getDP_TYPE_VALUE()   { "02" }    // [ 4 byte value ]
private getDP_TYPE_STRING()  { "03" }    // [ N byte string ]
private getDP_TYPE_ENUM()    { "04" }    // [ 0-255 ]
private getDP_TYPE_BITMAP()  { "05" }    // [ 1,2,4 bytes ] as bits

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
                if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
            } catch(e) {
                log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
            }
            def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
 log.trace "${device.displayName} now is: ${now()}"          
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
                if (settings?.logEnable) log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
            }
            
        } else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02")) {
            //if (descMap?.command == "02") { if (settings?.logEnable) log.warn "command == 02 !"  }            
            def dp = zigbee.convertHexToInt(descMap?.data[2])
            //def fncmd = 0
            def fncmd = getTuyaAttributeValue(descMap?.data)
/*            
            try {
                //  A negative index counts from the end of the list backward. So if we use -1 as index we get the last entry, if we use -2 as index we get the next-to-last entry.
                if ( descMap?.data.size() >= 6)) {
                    fncmd = zigbee.convertHexToInt(descMap?.data[6..-1].join(''))    // KK check / verify data length !!!! There are commands with just 4 data bytes !!
                }
                else {
                }
            } catch (e) {
                log.error "exception! dp=${dp} data = ${descMap?.data}"
            }
*/
            //log.trace "fncmd = ${fncmd}"
            if (dp == state.old_dp && fncmd == state.old_fncmd) {
                if (settings?.logEnable) log.warn "(duplicate) dp=${dp}  fncmd=${fncmd} command=${descMap?.command} data = ${descMap?.data}"
                return
            }
            //log.trace "dp=${dp} fncmd=${fncmd}"
            state.old_dp = dp
            state.old_fncmd = fncmd

            switch (dp) {
                case 0x01: // 0x01: Heat / Off
                    def mode = (fncmd == 0) ? "off" : "heat"
                    if (settings?.txtEnable) log.info "${device.displayName} Thermostat mode reported is: ${mode}"
                    sendEvent(name: "thermostatMode", value: mode, displayed: true)
                    if (mode == state.mode) {
                        state.mode = ""
                    }
                    break
                case 0x02: // added KK - Hold ??
                case 0x03: // 0x03 : Scheduled/Manual Mode
                    if (!(fncmd == 0)) {        // KK inverted
                        if (settings?.txtEnable) log.info "${device.displayName} Thermostat mode reported is: <b>scheduled</b>!"
                        log.trace "forceManual = ${settings?.forceManual}"
                        if (settings?.forceManual == true) {
                            if (settings?.logEnable) log.warn "calling setManualMode()"
                            setManualMode()
                        }
                        else {
                            log.trace "setManualMode() <b>not called!</b>"
                        }
                    } else {
                        log.info "${device.displayName} Thermostat mode reported is: manual"
                    }
                    // TODO - add event !!!
                    break
                case 0x10: // 0x10: Target Temperature / heating setpoint
                    def setpointValue = fncmd
                    if (device.getDataValue("manufacturer") == "_TZE200_ye5jkfsb") {
                        setpointValue = fncmd
                    }
                    else {
                        setpointValue = fncmd    // or ?
                    }
                    if (settings?.logEnable) log.info "${device.displayName} heatingSetpoint reported is: ${setpointValue}"
                    sendEvent(name: "heatingSetpoint", value: setpointValue as int, unit: "C", displayed: true)
                   // sendEvent(name: "coolingSetpoint", value: setpointValue as int, unit: "C", displayed: false)
                    sendEvent(name: "thermostatSetpoint", value: setpointValue as int, unit: "C", displayed: false)        // Google Home compatibility
                    if (setpointValue == state.setpoint)  {
                        state.setpoint = 0
                    }
                    break
                case 0x12: // Max Temp Limit
                    if (settings?.txtEnable) log.info "${device.displayName} Max Temp Limit reported is: ${fncmd}"
                    break
                case 0x13: // Max Temp 
                    if (settings?.txtEnable) log.info "${device.displayName} Max Temp reported is: ${fncmd}"
                    break
                case 0x14: // Dead Zone Temp
                    if (settings?.txtEnable) log.info "${device.displayName} Dead Zone Temp reported is: ${fncmd}"
                    break
                case 0x18: // 0x18 : Current (local) temperature
                    def currentTemperatureValue = fncmd// /10  KK was /10     
                    if (device.getDataValue("manufacturer") == "_TZE200_ye5jkfsb") {
                        currentTemperatureValue = fncmd 
                    }
                    else {
                        currentTemperatureValue = fncmd / 10
                    }
                    if (settings?.txtEnable) log.info "${device.displayName} temperature is: ${currentTemperatureValue}"
                    sendEvent(name: "temperature", value: currentTemperatureValue, unit: "C", displayed: true)
                    break
                case 0x1B: // Temp Correction (calibration)
                    if (settings?.txtEnable) log.info "${device.displayName} temperature correction reported is: ${fncmd}"
                    break                
                case 0x24: // 0x24 : current (running) operating state (valve)
                    if (settings?.txtEnable) log.info "${device.displayName} thermostatOperatingState reported is: ${fncmd ? "idle" : "heating"}"
                    sendEvent(name: "thermostatOperatingState", value: (fncmd ? "idle" : "heating"), displayed: true)
                    break
                case 0x28: // KK Child Lock
                    if (settings?.txtEnable) log.info "${device.displayName} Child Lock reported is: ${fncmd}"
                    break
                case 0x2B: // KK Sensor?
                    if (settings?.txtEnable) log.info "${device.displayName} Sensor reported is: ${fncmd}"
                    break
                case 0x65: // KK
                    if (settings?.txtEnable) log.info "${device.displayName} Thermostat PID regulation point is: ${fncmd}"
                    break
                case 0x2D: // KK Tuya cmd: dp=45 value=0 descMap.data = [00, 08, 2D, 05, 00, 01, 00]
                case 0x6C: // KK Tuya cmd: dp=108 value=404095046 descMap.data = [00, 08, 6C, 00, 00, 18, 06, 00, 28, 08, 00, 1C, 0B, 1E, 32, 0C, 1E, 32, 11, 00, 18, 16, 00, 46, 08, 00, 50, 17, 00, 3C]
                default:
                    if (settings?.logEnable) log.warn "${device.displayName} NOT PROCESSED Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                    break
            } //  (dp) switch
        } else {
            if (settings?.logEnable) log.warn "not parsed : "+descMap
        }
    } // if catchAll || readAttr
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
        default:
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
    if (settings?.txtEnable) log.info "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue("model")}"
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
    log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")}"
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
    log.trace "sendTuyaCommand = ${cmds}"
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
        default:
            break
    }
    sendZigbeeCommands( cmds )
}



