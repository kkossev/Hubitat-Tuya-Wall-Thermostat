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
 * ver. 1.0.0 2022-01-04 kkossev  - Inital version
 *
*/
import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

def version() { "1.0.0" } 

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
        
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: false)
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "forceManual", type: "enum", title: "Force Manual Mode : If the thermostat changes to schedule mode, then it automatically reverts to manual mode", options:["0": "Default", "1": "Force Manual Mode"], defaultValue: "0")
        
    }
}


private getCLUSTER_TUYA() { 0xEF00 }
private getSETDATA() { 0x00 }
private getSETTIME() { 0x24 }

// tuya DP type
private getDP_TYPE_BOOL() { "01" }
private getDP_TYPE_VALUE() { "02" }
private getDP_TYPE_ENUM() { "04" }

// Parse incoming device messages to generate events
def parse(String description) {
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
            log.debug "time synchronization request from device, descMap = ${descMap}"                       // doesn't seem to be working... :( 
            def offset = 0
            try {
                offset = location.getTimeZone().getOffset(new Date().getTime())
                //offset = getLocation().getTimeZone().getOffset(new Date().getTime())    // KK
                log.debug "timezone offset of current location is ${offset}"
            } catch(e) {
                log.error "cannot resolve current location. please set location in smartthings location setting. setting timezone offset to zero"
            }
            def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
           
            log.debug "sending time data :" + cmds
            cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
            state.old_dp = ""
            state.old_fncmd = ""
        } else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {
            log.debug "device has received data from clustercmd "+descMap?.data
            state.old_dp = ""
            state.old_fncmd = ""
        } else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02")) {
            def dp = zigbee.convertHexToInt(descMap?.data[2])
            def fncmd = 0
            try {
                fncmd = zigbee.convertHexToInt(descMap?.data[6..-1].join(''))
            } catch (e) {}
            //log.trace "fncmd = ${fncmd}"
            if (dp == state.old_dp && fncmd == state.old_fncmd) {
                log.warn "(duplicate) dp=${dp}  fncmd=${fncmd}"
                return
            }
            //log.trace "dp=${dp} fncmd=${fncmd}"
            state.old_dp = dp
            state.old_fncmd = fncmd

            switch (dp) {
                case 0x01: // 0x01: Heat / Off
                    def mode = (fncmd == 0) ? "off" : "heat"
                    log.info "Thermostat mode reported is: ${mode}"
                    sendEvent(name: "thermostatMode", value: mode, displayed: true)
                    if (mode == state.mode) {
                        state.mode = ""
                    }
                    break
                case 0x10: // 0x10: Target Temperature
                    def setpointValue = fncmd
                    log.debug "heatingSetpoint reported is: ${setpointValue}"
                    sendEvent(name: "heatingSetpoint", value: setpointValue as int, unit: "C", displayed: true)
                    sendEvent(name: "coolingSetpoint", value: setpointValue as int, unit: "C", displayed: false)
                    if (setpointValue == state.setpoint)  {
                        state.setpoint = 0
                    }
                    break
                case 0x18: // 0x18 : Current Temperature
                    def currentTemperatureValue = fncmd// /10  KK was /10     
                    log.info "current temperature reported is: ${currentTemperatureValue}"
                    sendEvent(name: "temperature", value: currentTemperatureValue, unit: "C", displayed: true)
                    break
                case 0x02: // added KK
                case 0x03: // 0x03 : Scheduled/Manual Mode
                    if (!(fncmd == 0)) {        // KK inverted
                        log.info "Thermostat mode reported is: <b>scheduled</b>!"
                        if (forceManual == "1") {
                            log.trace "calling setManualMode()"
                            setManualMode()
                        }
                    } else {
                        log.info "Thermostat mode reported is: manual"
                    }
                    break
                case 0x24: // 0x24 : operating state
                    log.debug "thermostatOperatingState reported is: ${fncmd ? "idle" : "heating"}"
                    sendEvent(name: "thermostatOperatingState", value: (fncmd ? "idle" : "heating"), displayed: true)
                    break
                case 0x65: // KK
                    log.info "Thermostat PID regulation point is: ${fncmd}"
                    break
                default:
                    log.warn "NOT PROCESSED Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                    break
            }
        } else {
            log.warn "not parsed : "+descMap
        }
    }
}

def setThermostatMode(mode){
    log.debug "setThermostatMode(${mode})"
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
    runIn(4, modeReceiveCheck, [overwrite:true])    // KK check!
    sendTuyaCommand("01", DP_TYPE_BOOL, mode=="heat" ? "01" : "00")
}

def setHeatingSetpoint(temperature){
    log.debug "setHeatingSetpoint(${temperature})"
    def settemp = temperature as int 
    settemp += (settemp != temperature && temperature > device.currentValue("heatingSetpoint")) ? 1 : 0
    log.debug "change setpoint to ${settemp}"
    state.setpoint = settemp
    runIn(4, setpointReceiveCheck, [overwrite:true])      // KK check!
    sendTuyaCommand("10", DP_TYPE_VALUE, zigbee.convertToHexString(settemp as int, 8))
}

def setCoolingSetpoint(temperature){
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
    log.debug "setManualMode()"
    ArrayList<String> cmds = []
    cmds = sendTuyaCommand("02", DP_TYPE_ENUM, "00") + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code
    sendZigbeeCommands( cmds )
}

def installed() {
    log.info "installed()"
    sendEvent(name: "supportedThermostatModes", value:  ["off", "heat"], isStateChange: true, displayed: true)
    sendEvent(name: "supportedThermostatFanModes", value: ["auto"])    
    sendEvent(name: "thermostatMode", value: "heat", displayed: false)
    sendEvent(name: "thermostatOperatingState", value: "idle", displayed: false)
    //sendEvent(name: "heatingSetpoint", value: 0, unit: "C", displayed: false)
    //sendEvent(name: "coolingSetpoint", value: 0, unit: "C", displayed: false)
    //sendEvent(name: "temperature", value: 0, unit: "C", displayed: false)
    state.mode = ""
    state.setpoint = 0
    unschedule()
    runEvery1Minute(receiveCheck)    // KK: check
}

def updated() {
    log.info "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue("model")}"
    log.info "Debug logging is <b>${logEnable}</b> Description text logging is  <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(1800, logsOff)    // turn off debug logging after 30 minutes
    }
    else {
        unschedule(logsOff)
    }
    log.info "Update finished"
}

def refresh() {
    if (logEnable) {log.debug "refresh()..."}
    //poll()
}

void initializeVars() {
    log.debug "${device.displayName} UnitializeVars()..."
    state.clear()
}

def logInitializeRezults() {
/*    
    log.info "${device.displayName} switchPollingSupported  = ${state.switchPollingSupported}"
    log.info "${device.displayName} voltagePollingSupported = ${state.voltagePollingSupported}"
    log.info "${device.displayName} currentPollingSupported = ${state.currentPollingSupported}"
    log.info "${device.displayName} powerPollingSupported   = ${state.powerPollingSupported}"
    log.info "${device.displayName} energyPollingSupported  = ${state.energyPollingSupported}"
*/
    log.info "${device.displayName} Initialization finished"
}

def configure() {
    initialize()
}

def initialize() {
    log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars()
    installed()
    updated()
    runIn( 5, logInitializeRezults)
}

def modeReceiveCheck() {
    log.debug "modeReceiveCheck()"
    if (state.mode != "") {
        log.debug " resending mode command :"+state.mode
        def cmds = setThermostatMode(state.mode)
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
    }
}

def setpointReceiveCheck() {
    log.debug "setpointReceiveCheck()"
    if (state.setpoint != 0 ) {
        log.debug " resending setpoint command :"+state.setpoint
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
    if (logEnable) {log.debug "sendZigbeeCommands(cmd=$cmd)"}
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
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def operationMode( mode ) {
    ArrayList<String> cmds = []
    
    switch (mode) {
        case "manual" : 
            cmds += sendTuyaCommand("02", DP_TYPE_ENUM, "00")// + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code
            log.trace "sending manual mode : ${cmds}"
            break
        case "program" :
            cmds += sendTuyaCommand("02", DP_TYPE_ENUM, "01")// + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code
            log.trace "sending program mode : ${cmds}"
            break
        default:
            break
    }
    sendZigbeeCommands( cmds )
}


