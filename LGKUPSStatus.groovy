/**
*   Device type to get ups status via telnet and set it in attributes to
* it can be used in rules.. needs to login.
*
*
* Assumptions: APC smart ups device with network card
*  
* lgk.com c 2020  free for personal use.. do not post
*
* version 1.2
*
*  You should have received a copy of the GNU General Public License
*  along with this program.  If not, see <https://www.gnu.org/licenses/>.
* 
* v 1.1 added desciprtive text and cleaned up debugging
* v 1.2 added changes due to varying net card firmware responses, also added some change debugging to help debug alternate smart ups net cards
*        also handle issue where some ups return on line , others online 
* v 1.3 apparently integer attributes dont work in rule machine . I assumed I needed them to be int to do value comparision but it wasn't working.
        Changed them to number not integer.
* v 1.4 added option to enable/disable.
* v 1.5 some ups return on line other on line handle both one with 8 words one with 4
* v 1.6 Add optional runtime for on battery so that you can check the UPS status fewer times and then increase
* the times check when on battery (ie reduce the time say from 30 minutes to 10 etc.)
* v 1.7 fix.. for yet another differing version of responses to get the UPS status. It seems there are as many differnt ups and net card firmwares and responses as days in the month!
* v 1.8 dont change ups status to unknown when starting a check, This was nice but causes and extra firing of any rule.
* v 1.9 add get ups temp both in celsius and f.
* v 1.10 as well as internal temp and battery attributes added them as capabilties as well so you can use standard battery and temp tiles and standard rules on these.
*    related also added a pulldown for units for temp ie C or F so the correct temp is set for the capability.
* 1.11 change for alternate etmp config
* v 2 added all kinds of new power and battery attributes. Not all UPS cards have all this info, It will report what it can.
* v 2.1 added two log levels, and auto turn off after 30 minutes.
* v 2.2 fixed typo in attribute name.
* v 2.3 cleared telnet error on close,init commands and also fixed version not passed correctly to attribute.
* v 2.4 change password input type to password from text
* v 2.5 aug 2022 apparently even if ups is charging but you set your low battery duration to a weird number like 20 minutes it will show battery status as discharged until it surpasses that
* so added discharged ups status.
* v 3.0 add new attribures model, serial number, manuf. date, firmware version and battery type.
* v 3.5 cannot do quit at end of battery and cannot include in initial string as the libraries now close the telnet immediately
* change order to detstatus -all last and close on battery sku which i hope every one returns.
* v 3.6 add outputWatts = outputCurrent * outputVoltage truncate to integer.
* v 3.7 fix issue where more that 59 minutes causes error add range to parameters.
* v 3.8 was turning off debugging even if set to minimal only turn off specifically if set to maximum.
* v 3.9 in order to get all output some were repeatedly coming out more than once, there was no way around this so added variabled to not put out more than once.
* also change messages that cannot be turned off to info instead of debug.

* v 4.0 add minute offset for runtime, so with multiple device say you schedule every 15 minutes with offset 2, it will run at 2 past the hour then 17 past the hour etc
* in this way you can have multiple ups's and have them run every x minutes but stagger them so they dont all run at the same time.
* v 4.1 only put out password if  max debug level set.. otherwise less info
* v 4.2 add connectStatus check if Trying in rule and if soo for a long time means it times out .. used to toggle a switch to reboot my wifi connector.
* v 4.3 add sku to differentiate between smt1500 and smt1500c 
* v 4.4 alternate get battery status on new f/w when says in green mode

*/

capability "Battery"
capability "Temperature Measurement"

attribute "lastCommand", "string"
attribute "hoursRemaining", "number"
attribute "minutesRemaining", "number"
attribute "UPSStatus", "string"
attribute "lastUpdate" , "string"
attribute "version", "string"
attribute "name", "string"
attribute "batteryPercentage" , "number"
attribute "currentCheckTime", "number"
attribute "CTemp", "number"
attribute "FTemp", "number"
attribute "telnet", "string"

attribute "outputVoltage", "number"
attribute "inputVoltage", "number"
attribute "outputFrequency", "number"
attribute "inputFrequency", "number"
attribute "outputWattsPercent", "number"
attribute "outputVAPercent", "number"
attribute "outputCurrent", "number"
attribute "outputEnergy", "number"
attribute "batteryVoltage", "number"
attribute "lastSelfTestResult", "string"
attribute "lastSelfTestDate", "string"
attribute "connectStatus", "string"
attribute "nextBatteryReplacementDate", "string"
attribute "serialNumber" , "string"
attribute "manufDate", "string"
attribute "model", "string"
attribute "firmwareVersion", "string"
attribute "batteryType", "string"
attribute "outputWatts", "number"
attribute "SKU", "string"
attribute "lastCommandResult", "string"

command "refresh"
command "UPS_Reboot"
command "UPS_Sleep"
command "UPS_RuntimeCalibrate"

command ("UPS_SetOutletGroup", [
                [ "name": "outletGroup",
                  "description": "Outlet Group 0 or 1",
                  "type": "ENUM",
                  "constraints": ["1","2"],
                 "required": true,
                      "default": "1"
                 ],
     
                [
                "name": "command",
                "description": "Command?",
                "type": "ENUM",
                "constraints": ["Off","On","DelayOff","DelayOn","Reboot","DelayReboot", "Shutdown","DelayShutdown","Cancel"],
                  "required": true
            
                ],
     
                 [
                 "name": "seconds",
                 "description": "Delay in seconds?",
                 "type": "ENUM",
                     "constraints": ["1","2","3","4","5","10","20","30","60","120","180","240","300","600"],
                     "required": true
                ]
                ]  )

preferences {
    input("UPSIP", "text", title: "Smart UPS (APC only) IP Address?", description: "Enter Smart UPS IP Address?", required: true)
    input("UPSPort", "integer", title: "Port #:", description: "Enter port number, default 23", defaultValue: 23)
    input("Username", "text", title: "Username for Login?", required: true, defaultValue: "")
    input("Password", "password", title: "Password for Login?", required: true, defaultValue: "")
    input("runTime", "number", title: "How often to check UPS Status  (in Minutes 1-59)?", required: true, defaultValue: 30, range: "1..59")  
    input("runOffset", "number", title: "Offset to run how many minutes past the hour, (used with multiple UPS's so they all don't run at once (0-59)?", required: true, defalutValue: 0, range: "0..59")
    input("runTimeOnBattery", "number", title: "How often to check UPS Status when on Battery (in Minutes 1-59)?", required: true, defaultValue: 10,range: "1..59")
    input("logLevel", "enum", title: "Logging Level (off,minimial,maximum) ?", options: ["off","minimal", "maximum"], required: true, defaultValue: "off")
    input("disable", "bool", title: "Disable?", required: false, defaultValue: false)
    input("tempUnits", "enum", title: "Units for Temperature Capabilty?", options: ["F","C"], required: true, defaultValue: "F")
}

metadata {
    definition (name: "LGK SmartUPS Status", namespace: "lgkapps", author: "larry kahn kahn@lgk.com") {
       capability "Refresh"
       capability "Actuator"
	   capability "Telnet"
	   capability "Configuration"
    }
}

def setversion(){
    state.name = "LGK SmartUPS Status"
	state.version = "4.4"
}

def installed() {
    initialize()
}

def updated() {
    initialize()   
}

def configure()
{
   initialize()   
}

def getloglevel()
{
    if (logLevel == "off")
    return(0)
    else if (logLevel == "minimal")
     return(1)
    else return(2)
}

def logsOff()
{
    device.updateSetting("logLevel", [value:"off", type:"enum"])
    log.warn "Debug logging disabled!"
}

def initialize() {  
    
    def scheduleString
 
    setversion()
    log.debug "$state.name, Version $state.version startng - IP = $UPSIP, Port = $UPSPort, debug/logging = $logLevel, Status update will run every $runTime minutes."
 	state.lastMsg = ""
    sendEvent(name: "lastCommand", value: "")
    sendEvent(name: "hoursRemaining", value: 1000)
    sendEvent(name: "minutesRemaining",value: 1000)
    //sendEvent(name: "UPSStatus", value: "Unknown")
    sendEvent(name: "version", value: state.version)
    sendEvent(name: "batteryPercentage", value: "???")
    sendEvent(name: "FTemp", value: 0.0)
    sendEvent(name: "CTemp", value: 0.0)
    sendEvent(name: "telnet", value: "Ok")
    sendEvent(name: "connectStatus", value: "Initialized")
    sendEvent(name: "lastCommandResult", value: "NA")
 
    if ((tempUnits == null) || (tempUnits == ""))
      device.tempUnits = "F"
    
    if (getloglevel() > 1) 
        log.debug "ip = $UPSIP, Port = $UPSPort, Username = $Username, Password = $Password"
    else
        log.info "ip = $UPSIP, Port = $UPSPort" 
        
    if ((UPSIP) && (UPSPort) && (Username) && (Password))
    {
     def now = new Date().format('MM/dd/yyyy h:mm a',location.timeZone)
     sendEvent(name: "lastUpdate", value: now, descriptionText: "Last Update: $now")
    
    unschedule()
        
     if (getloglevel() > 1) 
      {
        log.debug "Scheduling logging to turn off in 30 minutes."
        runIn(1800,logsOff)
      }
        
        
      // make sure inputs are integers note cannot do this directly inline in the update staements as they come out as 2.0 instead.
       
       def runTimeInt = runTime.toDouble().trunc().toInteger()
       def runTimeOnBatteryInt = runTimeOnBattery.toDouble().trunc().toInteger() 
       def runOffsetInt = runOffset.toDouble().trunc().toInteger()
           
       device.updateSetting("runTime", [value: runTimeInt , type:"number"])
       device.updateSetting("runTimeOnBattery", [value: runTimeOnBatteryInt , type:"number"])
       device.updateSetting("runOffset", [value: runOffsetInt, type: "number"])
        
        
     if (!disable)
        {
          if ((state.origAppName) && (state.origAppName != "") && (state.origAppName != device.getLabel()))
            {
                device.setLabel(state.origAppName)
            }
           
            if (tempUnits)
            {
                log.debug "Temp. Unit Currently: $tempUnits"
            }
            
            // only reset name if was not disabled
            if (state.disabled != true) state.origAppName =  device.getLabel()  
            state.disabled = false
            log.debug "Scheduling to run Every ${runTimeInt.toString()} Minutes, at ${runOffsetInt.toString()} past the hour."
            state.currentCheckTime = runTimeInt
            sendEvent(name: "currentCheckTime", value: state.currentCheckTime)
            scheduleString = "0 " + runOffsetInt.toString() + "/" + runTimeInt.toString() + " * ? * * *" 
           
           // scheduleString = "0 */" + runTimeInt.toString() + " * ? * * *"
            state.CronString = scheduleString
            if (getloglevel() > 0) log.debug "Schedule string = $scheduleString"
            
           schedule(scheduleString, refresh)
           sendEvent(name: "lastCommand", value: "Scheduled")     
           refresh()
         }
        
    else
    {
      log.debug "App. Disabled!"
      unschedule()
             
     if (getloglevel() > 0) 
      {
        log.debug "Scheduling logging to turn off in 30 minutes."
        runIn(60,logsOff)
      }
        
      if ((state.origAppName) && (state.origAppName != "")) 
     // change name if disbled or enabled
    
       device.setLabel(state.origAppName + " (Disabled)")
       state.disabled = true
       state.currentCheckTime = 0
       sendEvent(name: "currentCheckTime", value: state.currentCheckTime)
           
    }           
    }   
    else
    {
         log.debug "Parameters not filled in yet!"
    }
    
 }

def UPS_Reboot() 
{
   sendEvent(name: "lastCommandResult", value: "NA")
 
     
    if (getloglevel() > 0) log.info "Reboot called!"
  
    if (!disable)
    {
     
     if (getloglevel() > 0) log.debug "lgk SmartUPS Status Version ($state.version)"
      sendEvent(name: "lastCommand", value: "RebootConnect")
      sendEvent(name: "connectStatus", value: "Trying")
 
   
     if (getloglevel() > 0) log.debug "Connecting to ${UPSIP}:${UPSPort}"
	
	telnetClose()
	telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    }
  else
  { 
     log.debug "Reboot called but App is disabled. Will Not Run!"
  }
}

def UPS_Sleep()
{
   
     sendEvent(name: "lastCommandResult", value: "NA")
 
   
  if (getloglevel() > 0) log.info "Sleep called!"

  if (!disable)
    {
     
     if (getloglevel() > 0) log.debug "lgk SmartUPS Status Version ($state.version)"
      sendEvent(name: "lastCommand", value: "SleepConnect")
      sendEvent(name: "connectStatus", value: "Trying")
 
   
     if (getloglevel() > 0) log.debug "Connecting to ${UPSIP}:${UPSPort}"
	
	telnetClose()
	telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    }
  else
  { 
     log.debug "Sleep called but App is disabled. Will Not Run!"
  }
}  

def UPS_RuntimeCalibrate()
{
     sendEvent(name: "lastCommandResult", value: "NA") 
    
  if (getloglevel() > 0) log.info "Runtime Calibrate called!"

  if (!disable)
    {
     
     if (getloglevel() > 0) log.debug "lgk SmartUPS Status Version ($state.version)"
      sendEvent(name: "lastCommand", value: "CalibrateConnect")
      sendEvent(name: "connectStatus", value: "Trying")
 
   
     if (getloglevel() > 0) log.debug "Connecting to ${UPSIP}:${UPSPort}"
	
	telnetClose()
	telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    }
  else
  { 
     log.debug "Calibrate called but App is disabled. Will Not Run!"
  }
}  

def UPS_SetOutletGroup(p1, p2, p3)
{
    def goOn = true
    state.outlet = ""
    state.commmand = ""
    state.seconds = ""
 
   sendEvent(name: "lastCommandResult", value: "NA")   
    
    log.info "Set Outlet Group called! [$p1 $p2 $p3]"

    if (p1 == null)
    { 
        log.error "Outlet is required"
        goOn = false
    }
   else state.outlet = p1
  
   if (p2 == null)
     {
       log.error "Command is required!"
       goOn = false
     }
   else state.command = p2
       
   if (p3 == null)
     {
      // default delay to 0 if null
      state.seconds = "0"
     }
    else state.seconds = p3
  
    if (goOn)
    {  
        
    if (!disable)
    {
     if (getloglevel() > 0) log.debug "lgk SmartUPS Status Version ($state.version)"
      sendEvent(name: "lastCommand", value: "SetOutletGroupConnect")
      sendEvent(name: "connectStatus", value: "Trying")
 
     if (getloglevel() > 0) log.debug "Connecting to ${UPSIP}:${UPSPort}"
	
	telnetClose()
	telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    } // not disable
        
    
  else
  { 
     log.debug "SetOutletGroup called but App is disabled. Will Not Run!"
  } // disable
    } //go on
 }
       


def refresh() {
   
    if (!disable)
    {
       state.upsBattery = "Unknown"
       state.runtime = "Unknown"
       state.upsStatus = "Unknown"
       state.nextRunTime = "Unknown"
        
     if (getloglevel() > 0) log.debug "lgk SmartUPS Status Version ($state.version)"
      sendEvent(name: "lastCommand", value: "initialConnect")
      sendEvent(name: "connectStatus", value: "Trying")
 
   
     if (getloglevel() > 0) log.debug "Connecting to ${UPSIP}:${UPSPort}"
	
	telnetClose()
	telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    }
  else
  { 
     log.debug "Refresh called but App is disabled. Will Not Run!"
  }
 }

def sendData(String msg, Integer millsec) {
 if (getloglevel() > 1) log.debug "$msg"
	
	def hubCmd = sendHubCommand(new hubitat.device.HubAction("${msg}", hubitat.device.Protocol.TELNET))
	pauseExecution(millsec)
	
	return hubCmd
}

def parse(String msg) {
	 
    def lastCommand = device.currentValue("lastCommand")
    
    if (getloglevel() > 1) 
     {
        log.debug "In parse - (${msg})"
        log.debug "lastCommand = $lastCommand"
     }
    
    def pair = msg.split(" ")
  
    if (getloglevel() > 1)
    {
        log.debug ""
        log.debug "Got server response $msg value = $value lastCommand = ($lastCommand) length = ($pair.length)"
        log.debug ""
    }
    
   if (lastCommand == "RebootConnect") 
    
    {
      sendEvent(name: "connectStatus", value: "Connected")     
      sendEvent(name: "lastCommand", value: "Reboot")     
	        def sndMsg =[
	        		"$Username"
	        		, "$Password"
	        	    , "UPS -c reboot"
	            ]  
             def res1 = seqSend(sndMsg,500)
         }
    
    else if (lastCommand == "SleepConnect") 
    
    {
      sendEvent(name: "connectStatus", value: "Connected")     
      sendEvent(name: "lastCommand", value: "Sleep")     
	        def sndMsg =[
	        		"$Username"
	        		, "$Password"
	        	    , "UPS -c sleep"
	            ]  
             def res1 = seqSend(sndMsg,500)
         }
    
    else if (lastCommand == "CalibrateConnect") 
    
    {
      sendEvent(name: "connectStatus", value: "Connected")     
      sendEvent(name: "lastCommand", value: "RuntimeCalibrate")     
	        def sndMsg =[
	        		"$Username"
	        		, "$Password"
	        	    , "UPS -r start"
	            ]  
             def res1 = seqSend(sndMsg,500)
         }
    
   else if (lastCommand == "SetOutletGroupConnect") 
    
    {
      sendEvent(name: "connectStatus", value: "Connected")     
      sendEvent(name: "lastCommand", value: "SetOutletGroup")  
        
        if (getloglevel() > 1) log.debug "in set outlet group"
        if (getloglevel() > 1) log.debug "outlet = ${state.outlet}, command = ${state.command}, seconds = ${state.seconds}"
        
	     def sndMsg =[
	        		"$Username"
	        		, "$Password"
                    , "UPS -o ${state.outlet} ${state.command} ${state.seconds}"
	            ]  
           
             def res1 = seqSend(sndMsg,500)
         }
    
    else if (lastCommand == "initialConnect")
    
    {
      sendEvent(name: "connectStatus", value: "Connected")     
      sendEvent(name: "lastCommand", value: "getStatus")     
	        def sndMsg =[
	        		"$Username"
	        		, "$Password"
	        	    , "detstatus -rt"
                    , "detstatus -ss"
                    , "detstatus -soc"
                    , "detstatus -all"
                    , "detstatus -tmp"
                    , "upsabout"
                   
	            ]  
             def res1 = seqSend(sndMsg,500)
         }     
   
       else if (lastCommand == "quit")//  , "quit"
        { 
            sendEvent(name: "lastCommand", value: "Rescheduled")
            if (state.nextRunTime == "Unknown") log.info "Will run again in $state.currentCheckTime Minutes!"
            state.nextRunTime = state.currentCheckTime
            closeConnection()
            sendEvent([name: "telnet", value: "Ok"])
           } 
   else 
        {
            
       if (getloglevel() > 1) log.debug "In getstatus case length = $pair.length"
      
       if (pair.length == 5)
            {
              
             def p0 = pair[0]
             def p1 = pair[1]
             def p2 = pair[2]
             def p3 = pair[3]
             def p4 = pair[4]
             def firmware
                
             if (getloglevel() > 1) log.debug "p0 = $p0 p1 = $p1 p2 = $p2 p3 = $p3 p4 = $p4"
          
              if (p0 == "Output")
                 {
                    if ((p1 == "Watts") && (p2 == "Percent:"))
                     {
                        sendEvent(name: "outputWattsPercent", value: p3) 
                        if (getloglevel() > 0) log.debug "Output Watts Percent: $p3"
                     }      
                    else if ((p1 == "VA") && (p2 == "Percent:"))
                    {
                       sendEvent(name: "outputVAPercent", value: p3)
                       if (getloglevel() > 0) log.debug "Output VA Percent: $p3" 
                    }
                 }
               else if ((p0 == "Next") && (p1 == "Battery") && (p2 == "Replacement") && (p3 == "Date:")) 
               {
                   sendEvent(name: "nextBatteryReplacementDate", value: p4)
                   if (getloglevel() > 0) log.debug "Next Battery Replacment Date: $p4"
               }  
                
               else if ((p0 == "Firmware") && (p1 == "Revision:")) 
               {
                   firmware = p2 + " " + p3 + " " + p4
                   sendEvent(name: "firmwareVersion", value: firmware)
                   if (getloglevel() > 0) log.debug "Got Firmware version: $firmware"
               }          
              
            }  // length = 5        
        
       if (pair.length == 2)
            {
               def p0 = pair[0]
               def p1 = pair[1] 
                
               if (getloglevel() > 1) log.debug "p1 = $p0 p1 = $p1"
                
                if ( ((p0 == "E000:") || (p1 = "E001:")) && (p1 == "Success"))
                    {
                        if ((lastcommand == "Reboot") || (lastCommand == "Sleep") || (lastCommand == "RuntimeCalibrate") || (lastCommand == "SetOutletGroup"))
                        {
                          log.info "Command Sucessfully executed. [$p0, $p1]"
                          sendEvent(name: "lastCommandResult", value: "Success")
 
                          closeConnection()
                          sendEvent([name: "telnet", value: "Ok"])

                        }
                    }
                
                    else if ( (p0 == "E002:") || (p0 == "E100:") || (p0 == "E101:") || (p0 == "E102:") || (p0 == "E103:") || (p0 == "E107:") || (p0 == "E108:") )
                    {
                      log.error "Error: Command Returned [$p0, $p1]"
                      sendEvent(name: "lastCommandResult", value: "Failure")
   
                      closeConnection()
                      sendEvent([name: "telnet", value: "Ok"])
                    }
                    
                else if (p0 == "SKU:")
                  {
                    sendEvent(name: "SKU", value: p1)
                    if (getloglevel() > 0) log.debug "Got SKU: $p1"
                  }
            } // end length 2
                
       if (pair.length == 3)
            {
                
             def p0 = pair[0]
             def p1 = pair[1]
             def p2 = pair[2]
             def model
            
             if (getloglevel() > 1) log.debug "p0 = $p0 p1 = $p1 p2 = $p2"
          
                
             if ( (p0 == "E002:") || (p0 == "E100:") || (p0 == "E101:") || (p0 == "E102:") || (p0 == "E103:") || (p0 == "E107:") || (p0 == "E108:") )
                    {
                      log.error "Error: Command Returned [$p0, $p1 $p2]"
                       sendEvent(name: "lastCommandResult", value: "Failure") 
  
                      closeConnection()
                      sendEvent([name: "telnet", value: "Ok"])
                    }  
                
              else if ((p0 == "Self-Test") && (p1 == "Date:"))
                {
                         sendEvent(name: "lastSelfTestDate", value: p2) 
                         if (getloglevel() > 0) log.debug "Last Self Test Date: $p2"
                } 
               else if ((p0 == "Battery") && (p1 == "SKU:"))
               {
                     sendEvent(name: "batteryType", value: p2)
                     if (getloglevel() > 0) log.debug "Got Battery Type: $p2"
                   
                   sendEvent(name: "lastCommand", value: "quit")  
                   def res1 = sendData("quit",500)
                   
               }
               else if ((p0 == "Manufacture") && (p1 == "Date:"))
               {
                     sendEvent(name: "manufDate", value: p2)
                     if (getloglevel() > 0) log.debug "Got Manufacture Date: $p2"
               }  
               else if ((p0 == "Serial") && (p1 == "Number:"))
               {
                     sendEvent(name: "serialNumber", value: p2)
                     if (getloglevel() > 0) log.debug "Got Serial Number $p2"
               }  
              else if (p0 == "Model:")
               {
                   model = p1 + " " + p2
                   sendEvent(name: "model", value: model)
                   if (getloglevel() > 0) log.debug "Got Model: $model"
               }  
                   
            } // length = 3
            
       if (pair.length == 4)
            {
           
             def p0 = pair[0]
             def p1 = pair[1]
             def p2 = pair[2]
             def p3 = pair[3]
                
             if (getloglevel() > 1) log.debug "p0 = $p0 p1 = $p1 p2 = $p2 p3 = $p3"
             
             if ( (p0 == "E002:") || (p0 == "E100:") || (p0 == "E101:") || (p0 == "E102:") || (p0 == "E103:") || (p0 == "E107:") || (p0 == "E108:") )
                    {
                      log.error "Error: Command Returned [$p0, $p1 $p2 $p3]"
                      sendEvent(name: "lastCommandResult", value: "Failure")
 
  
                      closeConnection()
                      sendEvent([name: "telnet", value: "Ok"])
                    }
                
             else if (p0 == "Output")
                 {
                    if (p1 == "Voltage:")
                     {
                         sendEvent(name: "outputVoltage", value: p2) 
                         if (getloglevel() > 0) log.debug "Output Voltage: $p2"
                         state.outputVoltage = p2
                        
                     }  
                    else if (p1 == "Frequency:")
                    {
                        sendEvent(name: "outputFrequency", value: p2)
                        if (getloglevel() > 0) log.debug "Output Frequency: $p2"  
                    } 
                    else if (p1 == "Current:")
                    {
                        sendEvent(name: "outputCurrent", value: p2)
                        if (getloglevel() > 0) log.debug "Output Current: $p2"
                        
                         double watts = state.outputVoltage.toDouble() * p2.toDouble()
                         def intWatts = watts.toInteger()
                         sendEvent(name: "outputWatts", value: intWatts)
                    }
                    else if (p1 == "Energy:")
                    {
                        sendEvent(name: "outputEnergy", value: p2)  
                        if (getloglevel() > 0) log.debug "Output Energy: $p2"
                    }     
                 }
                
               else if (p0 == "Input")
                  {
                    if (p1 == "Voltage:")
                      {
                          sendEvent(name: "inputVoltage", value: p2)
                          if (getloglevel() > 0) log.debug "Input Voltage: $p2"
                      }
                    else if (p1 == "Frequency:")
                    {
                        sendEvent(name: "inputFrequency", value: p2) 
                        if (getloglevel() > 0) log.debug "Input Frequency: $p2"  
                    }
                  }
                
                else if ((p0 == "Battery") && (p1 == "Voltage:"))
                  {
                    sendEvent(name: "batteryVoltage", value: p2)
                    if (getloglevel() > 0) log.debug "Battery Voltage: $p2"  
                  }          
  
              
           
             if ((p0 == "Status") && (p1 == "of") && (p2 == "UPS:"))
                 {
                    def thestatus = p3
                    if (getloglevel() > 1) log.debug ""
                     // handle on line versus online case combiner p3 and p4
                    if ((p3 == "OnLine") || (p3 == "Online"))
                     {
                     thestatus = p3
                     }
                  
                       if ((thestatus == "OnLine,") || (thestatus == "Online"))
                         thestatus = "OnLine"
                       if ((thestatus == "Discharged,") || (thestatus == "Discharged"))
                         thestatus = "Discharged"
                       if (thestatus == "OnBattery,")
                         thestatus = "OnBattery"
                                     
                    if (getloglevel() > 1) log.info "*********************************"
                     if (state.upsStatus == "Unknown") log.info "Got UPS Status = $thestatus!"
                     state.upsStatus = theStatus
                    if (getloglevel() > 1) log.info "*********************************"
                     
                    sendEvent(name: "UPSStatus", value: thestatus)
                                  
                  if ((thestatus == "OnBattery") && (runTime != runTimeOnBattery) && (state.currentCheckTime != runTimeOnBattery))
                     {
                         log.debug "On Battery so Resetting Check time to $runTimeOnBattery Minutes!"
                         unschedule()
                         
                         scheduleString = "0 " + runOffset.toString() + "/" + runTimeOnBattery.toString() + " * ? * * *" 
           
                         //scheduleString = "0 */" + runTimeOnBattery.toString() + " * ? * * *"
                         if (getloglevel() > 1) log.debug "Schedule string = $scheduleString"
                         state.currentCheckTime = runTimeOnBattery
                         sendEvent(name: "currentCheckTime", value: state.currentCheckTime)
                         schedule(scheduleString, refresh)
                     } 
                   else if ((thestatus == "OnLine") && (runTime != runTimeOnBattery) && (state.currentCheckTime != runTime))
                     {
                       log.debug "UPS Back Online, so Resetting Check time to $runTime Minutes!"
                       unschedule()
                       scheduleString = "0 " + runOffset.toString() + "/" + runTime.toString() + " * ? * * *" 
            
                       //scheduleString = "0 */" + runTime.toString() + " * ? * * *"
                       if (getloglevel() > 1) log.debug "Schedule string = $scheduleString"
                       state.currentCheckTime = runTime
                       sendEvent(name: "currentCheckTime", value: state.currentCheckTime)
                       schedule(scheduleString, refresh)
                     }
                     
                 }
            } // length = 4
     
                
       if ((pair.length == 7) || (pair.length == 8) || (pair.length == 5) || (pair.length == 11))
         {
           def p0 = pair[0]
           def p1 = pair[1]
           def p2 = pair[2]
           def p3 = pair[3]
           def p4 = pair[4]
         
             if ( (p0 == "E002:") || (p0 == "E100:") || (p0 == "E101:") || (p0 == "E102:") || (p0 == "E103:") || (p0 == "E107:") || (p0 == "E108:") )
                    {
                      log.error "Error: Command Returned [$p0, $p1 $p2 $p3 $p4 $p5 $p6]"
                      sendEvent(name: "lastCommandResult", value: "Failure")
                        
                      closeConnection()
                      sendEvent([name: "telnet", value: "Ok"])
                    }     
    
             else if ((p0 == "Status") && (p1 == "of") && (p2 == "UPS:"))
                 {
                    def thestatus = p3
                   if (getloglevel() > 1) log.debug ""
                     // handle on line versus online case combiner p3 and p4
                    if ((p3 == "OnLine") || (p3 == "Online"))
                     {
                     thestatus = p3
                     }
                     else if (p3 == "On")
                     { 
                       thestatus = p3 + p4
                     }
                       if ((thestatus == "OnLine,") || (thestatus == "Online"))
                         thestatus = "OnLine"
                       if (thestatus == "OnBattery,")
                         thestatus = "OnBattery"
                     
                    if (getloglevel() > 1) log.info "*********************************"
                     if (state.upsStatus == "Unknown") log.info "Got UPS Status = $thestatus!"
                     state.upsStatus = theStatus
                    if (getloglevel() > 1) log.info "*********************************"
                     
                    sendEvent(name: "UPSStatus", value: thestatus)
                     
                    if ((thestatus == "OnBattery") && (runTime != runTimeOnBattery) && (state.currentCheckTime != runTimeOnBattery))
                     {
                         log.debug "On Battery so Resetting Check time to $runTimeOnBattery Minutes!"
                         unschedule()
                         scheduleString = "0 " + runOffset.toString() + "/" + runTimeOnBattery.toString() + " * ? * * *" 
           
                         //scheduleString = "0 */" + runTimeOnBattery.toString() + " * ? * * *"
                        if (getloglevel() > 1) log.debug "Schedule string = $scheduleString"
                         state.currentCheckTime = runTimeOnBattery
                         sendEvent(name: "currentCheckTime", value: state.currentCheckTime)
                         schedule(scheduleString, refresh)
                     } 
                   else if ((thestatus == "OnLine") && (runTime != runTimeOnBattery) && (state.currentCheckTime != runTime))
                     {
                       log.debug "UPS Back Online, so Resetting Check time to $runTime Minutes!"
                       unschedule()
                       scheduleString = "0 " + runOffset.toString() + "/" + runTime.toString() + " * ? * * *" 
              
                       //scheduleString = "0 */" + runTime.toString() + " * ? * * *"
                       if (getloglevel() > 1) log.debug "Schedule string = $scheduleString"
                       state.currentCheckTime = runTime
                       sendEvent(name: "currentCheckTime", value: state.currentCheckTime)
                       schedule(scheduleString, refresh)
                     }
                 }      
                          
            } // length = 7
     
      if (pair.length == 6)
         {
           def p0 = pair[0]
           def p1 = pair[1]
           def p2 = pair[2]
           def p3 = pair[3]
           def p4 = pair[4]
           def p5 = pair[5]
             
               if (getloglevel() > 1) log.debug "p0 = $p0 p1 = $p1 p2 = $p2 p3 = $p3 p4 = $p4 p5 = $p5"
                if ( (p0 == "E002:") || (p0 == "E100:") || (p0 == "E101:") || (p0 == "E102:") || (p0 == "E103:") || (p0 == "E107:") || (p0 == "E108:") )
                    {
                      log.error "Error: Command Returned [$p0, $p1 $p2 $p3 $p4 $p5]"
                       sendEvent(name: "lastCommandResult", value: "Failure")
    
                      closeConnection()
                      sendEvent([name: "telnet", value: "Ok"])
                    }  
             else 
             {
               if ((p0 == "Self-Test") && (p1 == "Result:"))
                  {
                    def theResult = p2 + " " +p3 + " " + p4 + " " + p5
                    sendEvent(name: "lastSelfTestResult", value: theResult)
                    if (getloglevel() > 0) log.debug "Last Self Test Result: $theResult"
                  }   

             if ((p0 == "Battery") && (p1 == "State") && (p3 == "Charge:"))
                 {
                    def p4dec = p4.toDouble() / 100.0
                    int p4int = p4dec * 100
                    
                    if (getloglevel() > 1) log.info "********************************"
                    if (state.upsBattery == "Unknown") log.info "UPS Battery Percentage: $p4!"
                      state.upsBattery = p4  
                    if (getloglevel() > 1) log.info "*********************************"
                   
                    sendEvent(name: "batteryPercentage", value: p4int)
                    sendEvent(name: "battery", value: p4int, unit: "%")
                 }  
             
             if (((p0 == "Internal") || (p0 == "Battery")) && (p1 == "Temperature:"))    
                 {   
                   if (getloglevel() > 1) log.info "********************************"
                   if (getloglevel() > 0) 
                     {
                         log.debug "Got C Temp = $p2!"
                         log.debug "Got F Temp = $p4!"
                     }
                    if (getloglevel() > 1) log.info "********************************"
      
                    sendEvent(name: "CTemp", value: p2)
                    sendEvent(name: "FTemp", value: p4)
                    if (tempUnits == "F")  
                      sendEvent(name: "temperature", value: p4, unit: tempUnits)
                    else 
                      sendEvent(name: "temperature", value: p2, unit: tempUnits)
                 }
   
             }
             
            } // length = 6
            
       if ((pair.length == 8) || (pair.length == 6))
         {
                
       def p0 = pair[0]
       def p1 = pair[1]
       def p2 = pair[2]
       def p3 = pair[3]
       def p4 = pair[4]
       def p5 = pair[5]
      

      if (getloglevel() > 1) log.debug "p0 = $p0 p1 = $p1 p2 = $p2 p3 = $p3 p4 = $p4 p5 = $p5"

     // looking for hours and minutes
     // Runtime Remaining: 2 hr 19 min 0 sec
             if ((p0 == "Runtime") && (p1 == "Remaining:") && (p3 == "hr"))
                 {    
                    if (getloglevel() > 1) log.info "********************************"
                    if (state.runtime == "Unknown") log.info "Got $p2 hours Remaining!"
                    if (getloglevel() > 1) log.info "********************************"
                     
                    sendEvent(name: "hoursRemaining", value: p2.toInteger())
                    state.hoursRemaining = p2.toInteger()
                 }
           
             if ((p0 == "Runtime") && (p1 == "Remaining:") && (p5 == "min"))
                 {   
                   if (getloglevel() > 1) log.info "********************************"
                   if (state.runtime == "Unknown") log.info "Got $p4 minutes Remaining!"
                    state.runtime = p4
                   if (getloglevel() > 1) log.info "********************************"
                     
                    sendEvent(name: "minutesRemaining", value: p4.toInteger())
                    state.minutesRemaining = p4.toInteger()
                     
                    def now = new Date().format('MM/dd/yyyy h:mm a',location.timeZone)
                    sendEvent(name: "lastUpdate", value: now, descriptionText: "Last Update: $now")

                 }
           
                } // length = 8
            
        if (pair.length == 10) // just for status of ups
         {
             
           def p0 = pair[0]
           def p1 = pair[1]
           def p2 = pair[2]
           def p3 = pair[3]
           def p4 = pair[4]
           def p5 = pair[5] 
             
            
             if ((p0 == "Status") && (p1 == "of") && (p2 == "UPS:"))
                 {
                    def thestatus = p3
                    if (getloglevel() > 1) log.debug ""
                     // handle on line versus online case combiner p3 and p4
                    if ((p3 == "OnLine") || (p3 == "Online"))
                     {
                     thestatus = p3
                     }
                  
                       if ((thestatus == "OnLine,") || (thestatus == "Online"))
                         thestatus = "OnLine"
                       if ((thestatus == "Discharged,") || (thestatus == "Discharged"))
                         thestatus = "Discharged"
                       if (thestatus == "OnBattery,")
                         thestatus = "OnBattery"
                                     
                    if (getloglevel() > 1) log.info "*********************************"
                     if (state.upsStatus == "Unknown") log.info "Got UPS Status = $thestatus!"
                     state.upsStatus = theStatus
                    if (getloglevel() > 1) log.info "*********************************"
                     
                    sendEvent(name: "UPSStatus", value: thestatus)
                                  
                  if ((thestatus == "OnBattery") && (runTime != runTimeOnBattery) && (state.currentCheckTime != runTimeOnBattery))
                     {
                         log.debug "On Battery so Resetting Check time to $runTimeOnBattery Minutes!"
                         unschedule()
                         
                         scheduleString = "0 " + runOffset.toString() + "/" + runTimeOnBattery.toString() + " * ? * * *" 
           
                         //scheduleString = "0 */" + runTimeOnBattery.toString() + " * ? * * *"
                         if (getloglevel() > 1) log.debug "Schedule string = $scheduleString"
                         state.currentCheckTime = runTimeOnBattery
                         sendEvent(name: "currentCheckTime", value: state.currentCheckTime)
                         schedule(scheduleString, refresh)
                     } 
                   else if ((thestatus == "OnLine") && (runTime != runTimeOnBattery) && (state.currentCheckTime != runTime))
                     {
                       log.debug "UPS Back Online, so Resetting Check time to $runTime Minutes!"
                       unschedule()
                       scheduleString = "0 " + runOffset.toString() + "/" + runTime.toString() + " * ? * * *" 
            
                       //scheduleString = "0 */" + runTime.toString() + " * ? * * *"
                       if (getloglevel() > 1) log.debug "Schedule string = $scheduleString"
                       state.currentCheckTime = runTime
                       sendEvent(name: "currentCheckTime", value: state.currentCheckTime)
                       schedule(scheduleString, refresh)
                     }
                     
                 }
            } // length = 10
        } 

    
}

def telnetStatus(status) {
    if (getloglevel() > 1) log.debug "telnetStatus: ${status}"
    sendEvent([name: "telnet", value: "${status}"])
}


def closeConnection()
{
    if (closeTelnet){
                try {
                    telnetClose()
                } catch(e) {
                   if (getloglevel() > 1) log.debug("Connection Closed")
                }
                
			}
}
    
boolean seqSend(msgs, Integer millisec)
{
    if (getloglevel() > 1) log.debug "in sendData"
  
			msgs.each {
				sendData("${it}",millisec)
			}
			seqSent = true
	return seqSent
}
