/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * lgk a few changes
 * 1. switch level capability so level changes get passed through and received from other apps
 * like dim with me. Now you can have this control a set of lights and dim based on this.
 * also reduced delays as the ge switches delay enough.
 * Finally, Added color control capability, rgb control tiles and associated functions,
 * and a color control tile.. I know the switch has nothing
 * to do with color. But since you use this device type to control slave lamps using either smart lighting
 * smart app or dim with me smartapp. Once this is in place you can use the color coordinator smartapp
 * and make this device the master to control the slave bulbs.
 * also works with other dimmers. Tested with GE (have delay) and Cooper (Instant). Can control hue lights with
 * no load hooked up. Recommend using Color with me App.

 */
 
metadata {
	definition (name: "Advanced Zwave Dimmer Switch w Color Control V2", namespace: "lgkapps", author: "kahn@lgk.com",  mnmn: "SmartThingsCommunity", vid: "3c16e1df-31b3-326e-91f4-215267497679" ) 
    {
		capability "Actuator"
		capability "Indicator"
		capability "Switch"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
        capability "Switch Level"
        capability "ColorControl"
 		 
        //command "setColor"
        //command "setAdjustedColor"

		fingerprint inClusters: "0x26"
	}

	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"
		status "09%": "command: 2003, payload: 09"
		status "10%": "command: 2003, payload: 0A"
		status "33%": "command: 2003, payload: 21"
		status "66%": "command: 2003, payload: 42"
		status "99%": "command: 2003, payload: 63"

		// reply messages
		reply "2001FF,delay 5000,2602": "command: 2603, payload: FF"
		reply "200100,delay 5000,2602": "command: 2603, payload: 00"
		reply "200119,delay 5000,2602": "command: 2603, payload: 19"
		reply "200132,delay 5000,2602": "command: 2603, payload: 32"
		reply "20014B,delay 5000,2602": "command: 2603, payload: 4B"
		reply "200163,delay 5000,2602": "command: 2603, payload: 63"
	}
}

preferences
{
  
    input name: "logEnable", title: "Enable debug logging", type: "bool", defaultValue: true
}

void logsOff()
{
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    log.warn "debug logging disabled"
}



def parse(String description) {
	def item1 = [
		canBeCurrentState: false,
		linkText: getLinkText(device),
		isStateChange: false,
		displayed: false,
		descriptionText: description,
		value:  description
	]
    
     if (logEnable) log.debug "in parse desc = $description"
	def result
	def cmd = zwave.parse(description, [0x20: 1, 0x26: 1, 0x70: 1])
	if (cmd) {
		result = createEvent(cmd, item1)
	}
	else {
		item1.displayed = displayed(description, item1.isStateChange)
		result = [item1]
	}
	if (logEnable) log.debug "Parse returned ${result?.descriptionText}"
	result
}

def createEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, Map item1) {
	def result = doCreateEvent(cmd, item1)
	for (int i = 0; i < result.size(); i++) {
		result[i].type = "physical"
	}
	result
}

def createEvent(hubitat.zwave.commands.basicv1.BasicSet cmd, Map item1) {
	def result = doCreateEvent(cmd, item1)
	for (int i = 0; i < result.size(); i++) {
		result[i].type = "physical"
	}
	result
}

def createEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStartLevelChange cmd, Map item1) {
	[]
}

def createEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStopLevelChange cmd, Map item1) {
	[response(zwave.basicV1.basicGet())]
}

def createEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelSet cmd, Map item1) {
	def result = doCreateEvent(cmd, item1)
	for (int i = 0; i < result.size(); i++) {
		result[i].type = "physical"
	}
	result
}

def createEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd, Map item1) {
	def result = doCreateEvent(cmd, item1)
	result[0].descriptionText = "${item1.linkText} is ${item1.value}"
	result[0].handlerName = cmd.value ? "statusOn" : "statusOff"
    
  if (logEnable)  log.debug "result size = $result.size"
	for (int i = 0; i < result.size(); i++) {
		result[i].type = "digital"
	}
    
	result
}

def doCreateEvent(hubitat.zwave.Command cmd, Map item1) {
	def result = [item1]

 if (logEnable) log.debug "in create event cmd = $cmd"
	item1.name = "switch"
	item1.value = cmd.value ? "on" : "off"
	item1.handlerName = item1.value
	item1.descriptionText = "${item1.linkText} was turned ${item1.value}"
	item1.canBeCurrentState = true
	item1.isStateChange = isStateChange(device, item1.name, item1.value)
	item1.displayed = item1.isStateChange

	if (cmd.value >= 5) {
		def item2 = new LinkedHashMap(item1)
		item2.name = "level"
		item2.value = cmd.value as String
		item2.unit = "%"
		item2.descriptionText = "${item1.linkText} dimmed ${item2.value} %"
		item2.canBeCurrentState = true
		item2.isStateChange = isStateChange(device, item2.name, item2.value)
		item2.displayed = false
        def  intlevel = item2.value as Integer
         setLevel(item2.value)// sendEvent(name:"switch.setLevel",value:intlevel)
		result << item2
	}
	result
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	def value = "when off"
	if (cmd.configurationValue[0] == 1) {value = "when on"}
	if (cmd.configurationValue[0] == 2) {value = "never"}
	[name: "indicatorStatus", value: value, display: false]
}

def createEvent(hubitat.zwave.Command cmd,  Map map) {
	// Handles any Z-Wave commands we aren't interested in
	if (logEnable) log.debug "UNHANDLED COMMAND $cmd"
}


def on() {
if (logEnable)	log.debug "in on"
	delayBetween([zwave.basicV1.basicSet(value: 0xFF).format(), zwave.switchMultilevelV1.switchMultilevelGet().format()], 1000)
}

def off() {
if (logEnable)	log.debug "in off"
	delayBetween ([zwave.basicV1.basicSet(value: 0x00).format(), zwave.switchMultilevelV1.switchMultilevelGet().format()], 3000)

}

def setLevel(value) {
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
    if (logEnable) log.debug "in set level new level = $level"
	if (level > 0) {
		sendEvent(name: "switch", value: "on")
	} else {
		sendEvent(name: "switch", value: "off")
	}
    //sendEvent(name:"switch.setLevel",value:level)
	delayBetween ([zwave.basicV1.basicSet(value: level).format(), zwave.switchMultilevelV1.switchMultilevelGet().format()], 500)
}
    
def setLevelo(value) {

	def valueaux = value as Integer
	def level = Math.min(valueaux, 99)
   if (logEnable) log.debug "in set level new level = $level"
	delayBetween ([zwave.basicV1.basicSet(value: level).format(), zwave.switchMultilevelV1.switchMultilevelGet().format(),
   				 	sendEvent(name:"switch.setLevel",value:level)], 500)
}


def setLevel(value, duration) {
if (logEnable) log.debug "in set level w duration new level = $value duration = $duration"
	def valueaux = value as Integer
	def level = Math.min(valueaux, 99)
  if (logEnable)  log.debug "in set level w duration new level = $level duration = $duration"
	def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
	zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration).format()
    sendEvent(name:"switch.setLevel",value:level)
}

def poll() {
	zwave.switchMultilevelV1.switchMultilevelGet().format()
    initialize()
}

def refresh()
{ log.debug "in refresh"
   // invertSwitch()
	zwave.switchMultilevelV1.switchMultilevelGet().format()
}

def indicatorWhenOn() {
	sendEvent(name: "indicatorStatus", value: "when on", display: false)
	zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 3, size: 1).format()
}

def indicatorWhenOff() {
	sendEvent(name: "indicatorStatus", value: "when off", display: false)
	zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 3, size: 1).format()
}

def indicatorNever() {
	sendEvent(name: "indicatorStatus", value: "never", display: false)
	zwave.configurationV1.configurationSet(configurationValue: [2], parameterNumber: 3, size: 1).format()
}

def invertSwitch(invert=true) {
if (logEnable) log.debug "in invert"
	if (invert) {
		zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 4, size: 1).format()
	}
	else {
    if (logEnable) log.debug "turn off invert"
		zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 4, size: 1).format()
	}
}


def setColor(value) {
if (logEnable)	log.debug "setColor: ${value}, $this"
	if (value.hue) { sendEvent(name: "hue", value: value.hue)}
	if (value.saturation) { sendEvent(name: "saturation", value: value.saturation)}
	if (value.hex) { sendEvent(name: "color", value: value.hex)}
	if (value.level) { sendEvent(name: "level", value: value.level)}
	}


def setAdjustedColor(value) {
	if (value) {
      if (logEnable)  log.trace "setAdjustedColor: ${value}"
        def adjusted = value + [:]
        adjusted.hue = adjustOutgoingHue(value.hue)
        // Needed because color picker always sends 100
        adjusted.level = null 
        setColor(adjusted)
    }
}

def adjustOutgoingHue(percent) {
	def adjusted = percent
	if (percent > 31) {
		if (percent < 63.0) {
			adjusted = percent + (7 * (percent -30 ) / 32)
		}
		else if (percent < 73.0) {
			adjusted = 69 + (5 * (percent - 62) / 10)
		}
		else {
			adjusted = percent + (2 * (100 - percent) / 28)
		}
	}
	if (logEnable) log.info "percent: $percent, adjusted: $adjusted"
	adjusted
}


def ping()
{
//command(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 0x01)) //poll the temperature to ping
}
def installed() {
	log.trace "Executing 'installed'"
    // use settings report time for check interval it is mintes
    	sendEvent(name: "checkInterval", value: 3600 , displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])

	initialize()
}



private initialize() {
	log.trace "Executing 'initialize'"

	sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
	sendEvent(name: "healthStatus", value: "online")
	sendEvent(name: "DeviceWatch-Enroll", value: [protocol: "cloud", scheme:"untracked"].encodeAsJson(), displayed: false)
}
