/*	===== HUBITAT INTEGRATION VERSION =====================================================
Hubitat - Samsung TV Remote Driver
		Copyright 2020 Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file
except in compliance with the License. You may obtain a copy of the License at:
		http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the  License.
===== DISCLAIMERS =========================================================================
		THE AUTHOR OF THIS INTEGRATION IS NOT ASSOCIATED WITH SAMSUNG. THIS CODE USES
		TECHNICAL DATA DERIVED FROM GITHUB SOURCES AND AS PERSONAL INVESTIGATION.
===== APPRECIATION ========================================================================
	Hubitat user Cal for technical, test, and emotional support.
	The GitHub WebSockets personnel for node.js code "ws" used in the external server
	GitHub user Toxblh for exlempary code for numerous commands
	Hubitat users who supported validation of 2016 - 2020 models.
===== REQUIREMENTS ========================================================================
a.	For model years 2017 and later, a stand-alone node.js server installed IAW provided
	instructions and running.
b.	This driver installed and configured IAW provided instructions.
===== Changes in this version =============================================================
(For earlier release note, see "https://github.com/DaveGut/HubitatActive/blob/master/SamsungTvRemote/Samsung%20Apps.txt")
1.3.7	a.	Removed play/pause attribute 'status'.  It only reflected the
			status, not the status of tv or running apps.
		b.	Added subscription to volume and mute attributes.  Will update
			even on physical remote keying of functions.
		c.	Added method appOpenByName function for named vice number ID apps.
		d.	Modified refresh.  It now gets only the SmartThings data (if ST
			integration is enabled) and the art mode status (if Frame TV).
		e.	Removed Notification and Speak Capabilities (did not work consistently)
		f.	Changed on/off methods to run quickPoll (one time) to determine switch state.
1.3.8	a.	Refined Quick Poll for better performance
		b.	Updated LAN functions to not run when device is off.
		c.	Updated On command to force a power poll if quickPoll is off.
		d.	Added path to archive version in the top notes section.
		e.	Clarified App Method names for clarity.
		f.	Modified method getDeviceData to create a DNI that is all caps
		g.	Modified ON wake-on-lan to use device DNI as the MAC
		YOU MUST RUN SAVE PREFERENCES AFTER INSTALLING THIS UPGRADE.
===== Issues with this version? =====
a.	Notify on Hubitat thread: 
	https://community.hubitat.com/t/samsung-hubitat-tv-integration-2016-and-later/55120/167
b.	Previous Ver URL:  
	https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungTvRemote/Archive/SamsungTVRemote.groovy
*/
def driverVer() { return "1.3.8" }
import groovy.json.JsonOutput
metadata {
	definition (name: "Samsung TV Remote",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungTvRemote/SamsungTVRemote.groovy"
			   ){
		capability "SamsungTV"			//	cmds: on/off, volume, mute. attrs: switch, volume, mute
		capability "Switch"
		//	===== UPnP Augmentation =====
		command "pause"					//	Only work on TV Players
		command "play"					//	Only work on TV Players
		command "stop"					//	Only work on TV Players
		capability "Refresh"
		//	===== WebSocketInterface =====
		command "close"					//	Force socket close.
		attribute "wsDeviceStatus", "string"	//	Socket status open/closed
		//	===== Remote Control Interface =====
		command "sendKey", ["string"]	//	Send entered key. eg: HDMI
		//	TV Art Display
		command "artModeOn"				//	Turns on Art Mode
		command "artModeOff"			//	Turns off Art Mode
		attribute "artModeStatus", "string"	//	on/off/notFrame
		command "ambientMode"			//	non-Frame TVs
		//	Cursor and Entry Control
		command "arrowLeft"
		command "arrowRight"
		command "arrowUp"
		command "arrowDown"
		command "enter"
		command "numericKeyPad"
		//	Menu Access
		command "home"
		command "menu"
		command "guide"
		command "info"					//	Pops up Info banner
		//	Source Commands
		command "source"				//	Pops up source window
		command "hdmi"					//	Direct progression through available sources
		command "setInputSource", [[	//	Requires SmartThings integration
			name: "Input Source",
			constraints: ["digitalTv", "HDMI1", "HDMI2", "HDMI3", "HDMI4", "COMPONENT"],
			type: "ENUM"]]
		attribute "inputSource", "string"		//	Requires SmartThings integration
		attribute "inputSources", "string"		//	Requires SmartThings integration
		//	TV Channel
		command "channelList"
		command "channelUp"
		command "channelDown"
		command "previousChannel"
		command "setTvChannel", ["string"]		//	Requires SmartThings integration
		attribute "tvChannel", "string"			//	Requires SmartThings integration
		attribute "tvChannelName", "string"		//	Requires SmartThings integration
		//	Playing Navigation Commands
		command "exit"
		command "Return"
		command "fastBack"
		command "fastForward"
		//	Application Access/Control
		command "appOpenByName", ["string"]
		command "appRunBrowser"
		command "appRunYouTube"
		command "appRunNetflix"
		command "appRunPrimeVideo"
		command "appRunYouTubeTV"
		command "appInstallByCode", ["string"]
		command "appOpenByCode", ["string"]
		command "appCloseNamedApp"
		//	===== Button Interface =====
		capability "PushableButton"
		command "push", ["NUMBER"]
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["off", "5", "10", "15", "20", "25", "30"],
			type: "ENUM"]]
		command "listStDevices"		//	Used only during ST Integration setup.
	}
	preferences {
		input ("deviceIp", "text", title: "Samsung TV Ip")
		input ("connectST", "bool", title: "Connect to SmartThings for added functions", defaultValue: false)
		if (connectST) {
			input ("stApiKey", "text", title: "SmartThings API Key")
			input ("stDeviceId", "text", title: "SmartThings TV Device ID")
		}
		input ("refreshInterval", "enum",  
			   title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "10", "15", "30"], defaultValue: "5")
		input ("tvPwrOnMode", "enum", title: "TV Startup Display", 
			   options: ["ART_MODE", "Ambient", "none"], defalutValue: "none")
		input ("debugLog", "bool",  title: "Enable debug logging for 30 minutes", defaultValue: true)
		input ("infoLog", "bool",  title: "Enable description text logging", defaultValue: true)
	}
}

//	===== Installation, setup and update =====
def installed() {
	state.token = "12345678"
	def tokenSupport = "false"
	updateDataValue("name", "Hubitat Samsung Remote")
	updateDataValue("name64", "Hubitat Samsung Remote".encodeAsBase64().toString())
}
def updated() {
	logInfo("updated")
	sendEvent(name: "numberOfButtons", value: "50")
	close()
	state.playQueue = []
	setUpnpData()
	def tokenSupport = getDeviceData()
	if (stApiKey && stDeviceId) {
		def stStatus = getStDeviceData("setup")
	}
	if(getDataValue("frameTv") == "false") {
		sendEvent(name: "artModeStatus", value: "notFrameTV")
	} else { getArtModeStatus() }
	if (debugLog) { runIn(1800, debugLogOff) }
	resubscribe()
	runEvery3Hours(resubscribe)
	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "10" : runEvery10Minutes("refresh"); break
		case "15" : runEvery15Minutes(refresh); break
		case "30" : runEvery30Minutes(refresh); break
		default:
			runEvery5Minutes(refresh); break
	}
	runIn(2, refresh)
}
def getDeviceData() {
	logInfo("getDeviceData: Updating Device Data.")
	def onOff = powerTest()
	if (onOff == "off") {
		logWarn("getDeviceData:  Update not completed.  TV is OFF")
		return
	}
	try{
		httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
			def wifiMac = resp.data.device.wifiMac
			updateDataValue("deviceMac", wifiMac)
			def newDni = wifiMac.replaceAll(":","").toUpperCase()
			device.setDeviceNetworkId(newDni)
			def modelYear = "20" + resp.data.device.model[0..1]
			updateDataValue("modelYear", modelYear)
			def frameTv = "false"
			if (resp.data.device.FrameTVSupport) {
				frameTv = resp.data.device.FrameTVSupport
			}
			updateDataValue("frameTv", frameTv)
			if (resp.data.device.TokenAuthSupport) {
				tokenSupport = resp.data.device.TokenAuthSupport
			}
			def uuid = resp.data.device.duid.substring(5)
			updateDataValue("uuid", uuid)
			updateDataValue("tokenSupport", tokenSupport)
			logInfo("getDeviceData: year = $modelYear, frameTv = $frameTv, tokenSupport = $tokenSupport")
		} 
	} catch (error) {
		logWarn("getDeviceData: Failed.  TV may be powered off.  Error = ${error}")
	}
		
	return tokenSupport
}
def setUpnpData() {
	logInfo("setUpnpData")
	updateDataValue("rcUrn", "urn:schemas-upnp-org:service:RenderingControl:1")
	updateDataValue("rcPath", "/upnp/control/RenderingControl1")
	updateDataValue("rcEvent", "/upnp/event/RenderingControl1")
	updateDataValue("rcPort", "9197")
	updateDataValue("avUrn", "urn:schemas-upnp-org:service:AVTransport:1")
	updateDataValue("avPath", "/upnp/control/AVTransport1")
	updateDataValue("avEvent", "/upnp/event/AVTransport1")
	updateDataValue("avPort", "9197")
}

//	========== UPnP Communications Functions ==========
private sendCmd(type, action, body = []){
	logDebug("sendCmd: type = ${type}, upnpAction = ${action}, upnpBody = ${body}")
	def cmdPort
	def cmdPath
	def cmdUrn
	if (type == "AVTransport") {
		cmdPort = getDataValue("avPort")
		cmdUrn = getDataValue("avUrn")
		cmdPath = getDataValue("avPath")
	} else if (type == "RenderingControl") {
		cmdPort = getDataValue("rcPort")
		cmdUrn = getDataValue("rcUrn")
		cmdPath = getDataValue("rcPath")
	} else { logWarn("sendCmd: Invalid UPnP Type = ${type}") }
	
	def host = "${deviceIp}:${cmdPort}"
	Map params = [path:	cmdPath,
				  urn:	 cmdUrn,
				  action:  action,
				  body:	body,
				  headers: [Host: host]]
	new hubitat.device.HubSoapAction(params)
}
def subscribe() {
	logDebug("subscribe")
	def address = device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
	def result = new hubitat.device.HubAction(
		method: "SUBSCRIBE",
		path: getDataValue("rcEvent"),
		headers: [
			HOST: "${deviceIp}:${getDataValue("rcPort")}",
			CALLBACK: "<http://${address}/rcParse",
			NT: "upnp:event",
			TIMEOUT: "Second-28800"])
	sendHubCommand(result)
}
def unsubscribe() {
	logDebug("unsubscribe: rcSid = ${getDataValue("rcSid")}")
	//	Will not work with rcSid = "", so exit
	if (device.currentValue("rcSid") == "") { return }
	def address = device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
	def result = new hubitat.device.HubAction(
		method: "UNSUBSCRIBE",
		path: getDataValue("rcEvent"),
		headers: [
			HOST: "${deviceIp}:${getDataValue("rcPort")}",
			SID: getDataValue("rcSid")])
	sendHubCommand(result)
	updateDataValue("rcSid", "")
}
def resubscribe() {
	logDebug("resubscribe: switch = ${device.currentValue("switch")}")
	//	If off, unsubscribe will not work.  However, need to set rcSid to "")
	if (device.currentValue("switch") == "off") {
		updateDataValue("rcSid", "")
		return
	}
	if (getDataValue("rcSid") != "") { unsubscribe() }
	runIn(5, subscribe)
}

//	===== WebSocket Communications =====
def connect(funct) {
	logDebug("connect: function = ${funct}")
	def url
	def name = getDataValue("name64")
	if (getDataValue("tokenSupport") == "true") {
		def token = state.token
		if (funct == "remote") {
			url = "wss://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}&token=${token}"
		} else if (funct == "frameArt") {
			url = "wss://${deviceIp}:8002/api/v2/channels/com.samsung.art-app?name=${name}&token=${token}"
		} else if (funct == "application") {
			url = "ws://${deviceIp}:8001/api/v2/applications?name=${name}"
		} else {
			logWarn("sendMessage: Invalid Function = ${funct}, tokenSupport = true")
		}
	} else {
		if (funct == "remote") {
			url = "ws://${deviceIp}:8001/api/v2/channels/samsung.remote.control?name=${name}"
		} else if (funct == "frameArt") {
			url = "ws://${deviceIp}:8001/api/v2/channels/com.samsung.art-app?name=${name}"
		} else if (funct == "application") {
			url = "ws://${deviceIp}:8001/api/v2?name=${name}"
		} else {
			logWarn("sendMessage: Invalid Function = ${funct}, tokenSupport = false")
		}
	}
	state.currentFunction = funct
	runIn(180, close)
	interfaces.webSocket.connect(url, ignoreSSLIssues: true)
}
def sendMessage(funct, data) {
	logDebug("sendMessage: function = ${funct} | data = ${data} | connectType = ${state.currentFunction}")
	if (device.currentValue("wsDeviceStatus") != "open" || state.currentFunction != funct) {
		connect(funct)
		pauseExecution(300)
	}
	interfaces.webSocket.sendMessage(data)
}
def close() {
	logDebug("close: webSocketClose")
	sendEvent(name: "wsDeviceStatus", value: "closed")
	state.currentFunction = "close"
	interfaces.webSocket.close()
}
def webSocketStatus(message) {
	if (message == "status: open") {
		sendEvent(name: "wsDeviceStatus", value: "open")
		logInfo("webSocketStatus: wsDeviceStatus = open")
	} else if (message == "status: closing") {
		sendEvent(name: "wsDeviceStatus", value: "closed")
		state.currentFunction = "close"
		logInfo("webSocketStatus: wsDeviceStatus = closed")
	} else if (message.substring(0,7) == "failure") {
		logInfo("webSocketStatus: Failure.  Closing Socket.")
		close()
	}
}

//	===== Parse the responses =====
def parse(resp) {
	if (resp.substring(2,6) == "data") {
		parseWebsocket(resp)
	} else {
		parseUpnp(resp)
	}
}
def parseUpnp(resp) {
	resp = parseLanMessage(resp)
	logDebug("parseUPnP: ${groovy.xml.XmlUtil.escapeXml(resp.body)}")
		if (!resp.body) {
		if (resp.headers.SID) {
			def sid = resp.headers.SID.trim()
			updateDataValue("rcSid", sid)
			logInfo("parse: updated rcSid to ${sid}")
		}
		return
	}
	def body =  new XmlSlurper().parseText(resp.body)
	def parts = body.toString().split('<')
	parts.each { part ->
		if (part.startsWith('Mute')) {
			part = part - "/>" - ' channel="Master" val'
			part = part.substring(part.length()-2).replaceAll('"','')
			def mute = "muted"
			if (part == "0") { mute = "unmuted" }
			sendEvent(name: "mute", value: mute)
			logDebug("parseUPnP: mute = ${mute}")
		}
		if (part.startsWith('Volume')) {
			part = part - "/>" - ' channel="Master" val'
			part = part.substring(part.length()-3).replaceAll('"','')
			sendEvent(name: "volume", value: part.toInteger())
			logDebug("parseUPnP: volume = ${part}")
		}
	}
}
def parseWebsocket(resp) {
	resp = parseJson(resp)
	logDebug("parseWebsocket: ${resp}")
	def event = resp.event
	def logMsg = "parseWebsocket: event = ${event}"
	if (event == "ms.channel.connect") {
		logMsg += ", webSocket open"
		def newToken = resp.data.token
		if (newToken != null && newToken != state.token) {
			logMsg += ", token updated to ${newToken}"
			logInfo("parseWebsocket: Token updated to ${newToken}")
			state.token = newToken
		}
	} else if (event == "d2d_service_message") {
		def data = parseJson(resp.data)
		if (data.event == "artmode_status" ||
			data.event == "art_mode_changed") {
			def status = data.value
			if (status == null) { status = data.status }
			sendEvent(name: "artModeStatus", value: status)
			logMsg += ", artMode status = ${data.value}"
			logInfo("parseWebsocket: artMode status = ${status}")
		}
	} else if (event == "ms.channel.ready") {
		logMsg += ", webSocket connected"
	} else if (event == "ms.error") {
		logMsg += "Error Event.  Closing webSocket"
		close{}
	} else {
		logMsg += ", message = ${resp}"
	}
	logDebug(logMsg)
}

//	===== SmartThings Communications / Parsing =====
private listStDevices() {
	if (!stApiKey) {
		logWarn("listStDevices: no stApiKey")
		return
	}
	logDebug("listDevices: Below is a list of SmartThings devices on your account.  Select the TV DeviceID and paste into the preferences section,")
	def cmdUri = "https://api.smartthings.com/v1/devices"
	def sendCmdParams = [
		uri: cmdUri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01',
				 'Authorization': 'Bearer ' + stApiKey.trim()]
	]
	httpGet(sendCmdParams) {resp ->
		def devicesData = resp.data.items
		devicesData.each {
			log.trace "Name = ${it.name} || DeviceId = ${it.deviceId}"
		}
	}
}
def getStDeviceStatus() { getStDeviceData("status") }
private getStDeviceData(reqType = "status") {
	if (!stDeviceId || !stApiKey) {
		logWarn("getStDeviceData: Missing ID or Key.")
		return
	}
	def cmdUri = "https://api.smartthings.com/v1/devices/${stDeviceId.trim()}/status"
	def sendCmdParams = [
		uri: cmdUri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01',
				 'Authorization': 'Bearer ' + stApiKey.trim()]
	]
	httpGet(sendCmdParams) {resp ->
		def data = resp.data.components.main
		if (resp.status == 200) {
			if (reqType == "status") {
				def inputSource = data.mediaInputSource.inputSource.value
				if (inputSource != device.currentValue("inputSource")) {
					sendEvent(name: "inputSource", value: inputSource)
				}
				def tvChannel = data.tvChannel.tvChannel.value
				def tvChannlName = data.tvChannel.tvChannelName.value
				if (tvChannel != getDataValue("tvChannel")) {
					sendEvent(name: "tvChannel", value: tvChannel)
					sendEvent(name: "tvChannelName", value: tvChannelName)
				}
				logDebug("getStDeviceData: source = ${inputSource}, channel = ${tvChannel}")
			} else if (reqType == "setup") {
				def inputSources = data.mediaInputSource.supportedInputSources.value
				sendEvent(name: "inputSources", value: inputSources)
				logDebug("getStDeviceData: inputSources = ${inputSources}")
			}
		} else { logWarn{"getStDeviceData: Invalid resp status.  Status = ${resp.status}"} }
	}
}
private sendStPost(cap, cmd, args = null){
	if (!stDeviceId || !stApiKey) {
		logWarn("sendStPost: no stApiKey or stDeviceId")
		return
	}
	logDebug("sendStGet: ${comp} / ${cap}/ ${cmd}/ ${args} / ${source}")
	def cmdUri =  "https://api.smartthings.com/v1/devices/${stDeviceId.trim()}/commands"
	def cmd1 = [
		component: "main",
		capability: cap,
		command: cmd,
		arguments: args
	]
	def cmdBody = [commands: [cmd1]]
	def sendCmdParams = [
		uri: cmdUri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01',
				 'Authorization': 'Bearer ' + stApiKey.trim()],
		body : new groovy.json.JsonBuilder(cmdBody).toString()
	]
	try {
		httpPost(sendCmdParams) {resp ->
			def data = resp.data
			if (resp.status == 200) {
				log.trace data.results[0].status
				if (data.results[0].status == "ACCEPTED") {
					getStDeviceData()
				} else {
					logWarn("sendStPost: Status = ${data.results.status}")
				}
	
			} else { logWarn{": Invalid resp status.  Status = ${resp.status}"} }
		}
	} catch (e) { logWarn("sendStPost: Invalid Argument. error = ${e}") }
}

//	===== Capability Samsung TV =====
def on() {
	logDebug("on: desired TV Mode = ${tvPwrOnMode}")
	def wol = new hubitat.device.HubAction ("wake on lan ${device.deviceNetworkId}",
											hubitat.device.Protocol.LAN,
											null)
	sendHubCommand(wol)
	runIn(30, quickPoll)
}
def off() {
	logDebug("off: frameTv = ${getDataValue("frameTV")}")
	if (getDataValue("frameTv") == "false") { sendKey("POWER") }
	else {
		sendKey("POWER", "Press")
		pauseExecution(3000)
		sendKey("POWER", "Release")
	}
	runIn(10, quickPoll)
}

def mute() { 
	sendKey("MUTE")
}
def unmute() {
	sendKey("MUTE")
}
def setVolume(volume) {
	logDebug("setVolume: volume = ${volume}")
	volume = volume.toInteger()
	if (volume <= 0 || volume >= 100) { return }
	sendCmd("RenderingControl",
			"SetVolume",
			["InstanceID" :0,
			 "Channel": "Master",
			 "DesiredVolume": volume])
}
def volumeUp() {
	sendKey("VOLUP")
}
def volumeDown() {
	sendKey("VOLDOWN")
}
def play() { sendKey("PLAY") }
def pause() { sendKey("PAUSE") }
def stop() { sendKey("STOP") }
def setPictureMode(data) { logDebug("setPictureMode: not implemented") }
def setSoundMode(data) { logDebug("setSoundMode: not implemented") }
def showMessage(d,d1,d2,d3) { logDebug("showMessage: not implemented") }

//	===== Quick Polling Capability =====
def setPollInterval(interval) {
	logDebug("setPollInterval: interval = ${interval}")
	if (interval == "off") {
		state.quickPoll = false
		state.pollInterval = "off"
		state.remove("WARNING")
		unschedule(quickPoll)
	} else {
		state.quickPoll = true
		state.pollInterval = interval
		schedule("*/${interval} * * * * ?",  quickPoll)
		logWarn("setPollInterval: polling interval set to ${interval} seconds.\n" +
				"Quick Polling can have negative impact on the Hubitat Hub performance. " +
			    "If you encounter performance problems, try turning off quick polling.")
		state.WARNING = "<b>Quick Polling can have negative impact on the Hubitat " +
						"Hub and network performance.</b>  If you encounter performance " +
				    	"problems, <b>before contacting Hubitat support</b>, turn off quick " +
				    	"polling and check your sysem out."
	}
}
def quickPoll() {
	def onOff = powerTest()
	if (device.currentValue("switch") != onOff) {
		sendEvent(name: "switch", value: onOff)
		powerOnActions()
	}
}
def powerTest() {
	try {
		httpGet([uri: "http://${deviceIp}:9197/dmr", timeout: 5]) { resp ->
			return "on"
		}
	} catch (error) {
		return "off"
	}
}
def powerOnActions() {
	if(tvPwrOnMode == "ART_MODE" && getDataValue("frameTv") == "true") {
		artMode("on") }
	else if(tvPwrOnMode == "Ambient") { ambientMode() }
	else { connect("remote") }
	runIn(5, resubscribe)
}

//	========== Capability Refresh ==========
def refresh() {
	def onOff = quickPoll()
	pauseExecution(5000)
	if (device.currentValue("switch") == "off") {
		logDebug("refresh: TV is off.  Refresh methods not run.")
		return
	}
	logDebug("refresh: getting artMode and SmartThings status data")
	getArtModeStatus()
	runIn(1, getStDeviceStatus)
}

//	===== Samsung Smart Remote Keys =====
def sendKey(key, cmd = "Click") {
	key = "KEY_${key.toUpperCase()}"
	def data = [method:"ms.remote.control",
				params:[Cmd:"${cmd}",
						DataOfCmd:"${key}",
						TypeOfRemote:"SendRemoteKey"]]
	sendMessage("remote", JsonOutput.toJson(data) )
}
//	TV Art Display
def artModeOn() {
	artMode("on")
}
def artModeOff() {
	artMode("off")
}
def artMode(onOff) {
	logDebug("artMode: ${onOff}")
	def data = [value:"${onOff}",
				request:"set_artmode_status",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	artModeCmd(data)
}
def getArtModeStatus() {
	def data = [request:"get_artmode_status",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	artModeCmd(data)
}
def artModeCmd(data) {
	logDebug("artModeCmd: frameTv = ${getDataValue("frameTv")}")
	if (getDataValue("frameTv") == "false") { return }
	def cmdData = [method:"ms.channel.emit",
				   params:[data:"${data}",
						   to:"host",
						   event:"art_app_request"]]
	cmdData = JsonOutput.toJson(cmdData)
	sendMessage("frameArt", cmdData)	//	send command, connect is automatic.
}
def ambientMode() {
	logDebug("ambientMode: frameTv = ${getDataValue("frameTv")}")
	if (getDataValue("frameTv") == "true") { return }
	sendKey("AMBIENT")
}
//	Cursor and Entry Control
def arrowLeft() { sendKey("LEFT") }
def arrowRight() { sendKey("RIGHT") }
def arrowUp() { sendKey("UP") }
def arrowDown() { sendKey("DOWN") }
def enter() { sendKey("ENTER") }
def numericKeyPad() { sendKey("MORE") }
//	Menu Access
def home() { sendKey("HOME") }
def menu() { sendKey("MENU") }
def guide() { sendKey("GUIDE") }
def info() { sendKey("INFO") }
//	Source Commands
def source() { 
	sendKey("SOURCE")
	runIn(5, getStDeviceStatus)
}
def hdmi() {
	sendKey("HDMI")
	runIn(5, getStDeviceStatus)
}
def setInputSource(inputSource) {
	sendStPost("mediaInputSource", "setInputSource", args = [inputSource])
}
//	TV Channel
def channelList() { sendKey("CH_LIST") }
def channelUp() { sendKey("CHUP") }
def channelDown() { sendKey("CHDOWN") }
def previousChannel() { sendKey("PRECH") }
def setTvChannel(tvChannel) {
	sendStPost("tvChannel", "setTvChannel", args = ["11"])
}
//	Playing Navigation Commands
def exit() { sendKey("EXIT") }
def Return() { sendKey("RETURN") }
def fastBack() {
	sendKey("LEFT", "Press")
	pauseExecution(1000)
	sendKey("LEFT", "Release")
}
def fastForward() {
	sendKey("RIGHT", "Press")
	pauseExecution(1000)
	sendKey("RIGHT", "Release")
}
//	Application Access/Control
def appOpenByName(appName) {
	def url = "http://${deviceIp}:8080/ws/apps/${appName}"
	httpPost(url, "") { resp ->
		logDebug("#{appName}:  ${resp.status}  ||  ${resp.data}")
	}
}
def appRunBrowser() { sendKey("CONVERGENCE") }
def appRunYouTube() {
	def url = "http://${deviceIp}:8080/ws/apps/YouTube"
	httpPost(url, "") { resp ->
		logDebug("youTube:  ${resp.status}  ||  ${resp.data}")
	}
}
def appRunNetflix() {
	def url = "http://${deviceIp}:8080/ws/apps/Netflix"
	httpPost(url, "") { resp ->
		logDebug("netflix:  ${resp.status}  ||  ${resp.data}")
	}
}
def appRunPrimeVideo() {
	def url = "http://${deviceIp}:8080/ws/apps/AmazonInstantVideo"
	httpPost(url, "") { resp ->
		logDebug("primeVideo:  ${resp.status}  ||  ${resp.data}")
	}
}
def appRunYouTubeTV() {
	def url = "http://${deviceIp}:8080/ws/apps/YouTubeTV"
	httpPost(url, "") { resp ->
		logDebug("primeVideo:  ${resp.status}  ||  ${resp.data}")
	}
}
def appInstallByCode(appId) {
	logDebug("appInstall: appId = ${appId}")
	def uri = "http://${deviceIp}:8001/api/v2/applications/${appId}"
	try {
		httpPut(uri, "") { resp ->
			if (resp.data == true) {
				logDebug("appOpen: Success.")
			}
		}
	} catch (e) {
		logWarn("appInstall: appId = ${appId}, FAILED: ${e}")
		return
	}
}
def appOpenByCode(appId) {
	def uri = "http://${deviceIp}:8001/api/v2/applications/${appId}"
	try {
		httpPost(uri, body) { resp ->
			logDebug("appOpen: appId = ${appId}, Success.")
		}
	} catch (e) {
		logWarn("appOpen: appId = ${appId}, FAILED: ${e}")
		return
	}
	runIn(5, appGetData, [data: appId]) 
}
def appGetData(appId) {
	def uri = "http://${deviceIp}:8001/api/v2/applications/${appId}"
	try {
		httpGet(uri) { resp -> 
			state.currentAppId = resp.data.id
			logDebug("appGetData: appId = ${resp.data.id}")
		}
	} catch (e) {
		state.latestAppData = [id: appId]
		logWarn("appGetData: appId = ${appId}, FAILED: ${e}")
	}
}
def appCloseNamedApp() {
	def appId = state.currentAppId
	logDebug("appClose: appId = ${appId}")
	def uri = "http://${deviceIp}:8001/api/v2/applications/${appId}"
	try { httpDelete([uri: uri]) { resp -> }
	} catch (e) {}
	state.currentAppId = null
}

//	===== Button Interface (facilitates dashboard integration) =====
def push(pushed) {
	logDebug("push: button = ${pushed}, trigger = ${state.triggered}")
	if (pushed == null) {
		logWarn("push: pushed is null.  Input ignored")
		return
	}
	sendEvent(name: "pushed", value: pushed)
	pushed = pushed.toInteger()
	switch(pushed) {
		case 0 : close(); break
		//	===== Physical Remote Commands =====
		case 3 : numericKeyPad(); break
		case 5 : artModeOn(); break			//	New command.  Toggles art mode
		case 6 : artModeOff(); break			//	New command.  Toggles art mode
		case 7 : ambientMode(); break
		case 8 : arrowLeft(); break
		case 9 : arrowRight(); break
		case 10: arrowUp(); break
		case 11: arrowDown(); break
		case 12: enter(); break
		case 13: exit(); break
		case 14: home(); break
		case 18: channelUp(); break
		case 19: channelDown(); break
		case 20: guide(); break
		//	===== Direct Access Functions
		case 23: menu(); break			//	Main menu with access to system settings.
		case 24: source(); break		//	Pops up home with cursor at source.  Use left/right/enter to select.
		case 25: info(); break			//	Pops up upper display of currently playing channel
		case 26: channelList(); break	//	Pops up short channel-list.
		case 27: source0(); break		//	Direct to source TV
		case 28: source1(); break		//	Direct to source 1 (one right of TV on menu)
		case 29: source2(); break		//	Direct to source 1 (two right of TV on menu)
		case 30: source3(); break		//	Direct to source 1 (three right of TV on menu)
		case 31: source4(); break		//	Direct to source 1 (ofour right of TV on menu)
		//	===== Other Commands =====
		case 34: previousChannel(); break
		case 35: hdmi(); break			//	Brings up next available source
		case 36: fastBack(); break		//	causes fast forward
		case 37: fastForward(); break	//	causes fast rewind
		case 38: browser(); break		//	Direct to source 1 (ofour right of TV on menu)
		case 39: youTube(); break
		case 40: netflix(); break
		default:
			logDebug("push: Invalid Button Number!")
			break
	}
}
//	===== Logging =====
def logInfo(msg) { 
	if (infoLog == true) {
		log.info "${device.deviceNetworkId}, ${driverVer()} || ${msg}"
	}
}
def debugLogOff() {
	device.updateSetting("debugLog", [type:"bool", value: false])
	logInfo("Debug logging is false.")
}
def logDebug(msg) {
	if (debugLog == true) {
		log.debug "${device.deviceNetworkId}, ${driverVer()} || ${msg}"
	}
}
def logWarn(msg) { log.warn "${device.deviceNetworkId}, ${driverVer()} || ${msg}" }