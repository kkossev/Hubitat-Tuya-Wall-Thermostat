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
        capability "Thermostat"
		capability "Temperature Measurement"
        
        command "test"
        command "initialize"

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ye5jkfsb",  deviceJoinName: "Tuya Wall Thermostat" 
        
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: false)
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
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
            log.trace "fncmd = ${fncmd}"
            if (dp == state.old_dp && fncmd == state.old_fncmd) {
                log.warn "(duplicate) dp=${dp}  fncmd=${fncmd}"
                return
            }
            log.debug "dp=${dp} fncmd=${fncmd}"
            state.old_dp = dp
            state.old_fncmd = fncmd

            switch (dp) {
                case 0x01: // 0x01: Heat / Off
                    def mode = (fncmd == 0) ? "off" : "heat"
                    log.debug "mode: ${mode}"
                    sendEvent(name: "thermostatMode", value: mode, displayed: true)
                    if (mode == state.mode) {
                        state.mode = ""
                    }
                    break
                case 0x10: // 0x10: Target Temperature
                    def setpointValue = fncmd
                    log.debug "target temp: ${setpointValue}"
                    sendEvent(name: "heatingSetpoint", value: setpointValue as int, unit: "C", displayed: true)
                    sendEvent(name: "coolingSetpoint", value: setpointValue as int, unit: "C", displayed: false)
                    if (setpointValue == state.setpoint)  {
                        state.setpoint = 0
                    }
                    break
                case 0x65: // KK also sent to DP 101 ( 0x65 ) !, but is 1 deg. less than the tmp shown on the displa?y... Igonore!
                    log.info "Ignoring command 0x65 ($fncmd)"
                    break
                case 0x18: // 0x18 : Current Temperature
                    def currentTemperatureValue = fncmd// /10  KK was /10     
                    log.debug "current temp: ${currentTemperatureValue}"
                    sendEvent(name: "temperature", value: currentTemperatureValue, unit: "C", displayed: true)
                    break
                case 0x02: // added KK
                case 0x03: // 0x03 : Scheduled/Manual Mode
                    if (!(fncmd == 0)) {        // KK inverted
                        log.warn "scheduled mode"
                        if (forceManual == "1") {
                            setManualMode()
                        }
                    } else {
                        log.warn "manual mode"
                    }
                    break
                case 0x24: // 0x24 : operating state
                    log.debug "thermostatOperatingState = ${fncmd}"
                    sendEvent(name: "thermostatOperatingState", value: (fncmd ? "idle" : "heating"), displayed: true)
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
    state.mode = mode
    runIn(4, modeReceiveCheck, [overwrite:true])
    sendTuyaCommand("01", DP_TYPE_BOOL, (mode=="heat")?"01":"00")
}

def setHeatingSetpoint(temperature){
    log.debug "setHeatingSetpoint(${temperature})"
    def settemp = temperature as int 
    settemp += (settemp != temperature && temperature > device.currentValue("heatingSetpoint")) ? 1 : 0
    log.debug "change setpoint to ${settemp}"
    state.setpoint = settemp
    runIn(4, setpointReceiveCheck, [overwrite:true])  
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

def setManualMode() {
    log.debug "setManualMode()"
    //def cmds = sendTuyaCommand("02", DP_TYPE_ENUM, "00") + sendTuyaCommand("03", DP_TYPE_ENUM, "01")    // iquix code
    def cmds = /*sendTuyaCommand("02", DP_TYPE_ENUM, "00") +*/ sendTuyaCommand("04", DP_TYPE_ENUM, "02")
    //def cmds = sendTuyaCommand("02", DP_TYPE_ENUM, "00")        // does not work .. :( 
    cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }     
}

def installed() {
    log.info "installed()"
    sendEvent(name: "supportedThermostatModes", value: JsonOutput.toJson(["heat", "off"]), displayed: false)
    sendEvent(name: "thermostatMode", value: "off", displayed: false)
    sendEvent(name: "thermostatOperatingState", value: "idle", displayed: false)
    sendEvent(name: "heatingSetpoint", value: 0, unit: "C", displayed: false)
    //sendEvent(name: "coolingSetpoint", value: 0, unit: "C", displayed: false)
    sendEvent(name: "temperature", value: 0, unit: "C", displayed: false)
    state.mode = ""
    state.setpoint = 0
    unschedule()
    runEvery1Minute(receiveCheck)
}

def updated() {
    log.info "updated()"
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
    def cmds = zigbee.command(CLUSTER_TUYA, SETDATA, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    log.trace "sendTuyaCommand = ${cmds}"
    cmds
}

private getPACKET_ID() {
    state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
    return zigbee.convertToHexString(state.packetID, 4)
    /*
    def pktId =  zigbee.convertToHexString(state.packetID, 4)
    log.trace "pktId = ${pktId}"
    return pktId
*/
}





