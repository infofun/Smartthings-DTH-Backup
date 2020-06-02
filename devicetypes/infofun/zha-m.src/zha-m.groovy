import groovy.json.JsonOutput
import physicalgraph.zigbee.zcl.DataType

metadata {
    definition (name: "ZHA_m", namespace: "infofun", author: "infofun") {
        capability "Configuration"
        capability "Battery"
        capability "Refresh"
        capability "Button"
		capability "Holdable Button" 
        capability "Sensor"
		capability "Health Check"

        fingerprint profileId: "0104", endpointId: "02", application:"02", outClusters: "0019", inClusters: "0000,0001,0003,000F,FC00", manufacturer: "Philips", model: "RWL020", deviceJoinName: "Hue Dimmer Switch (ZHA)"

        attribute "lastAction", "string"
    }


    simulator {
        // TODO: define status and reply messages here
    }
	
	preferences {
        section {
            input ("holdTime", "number", title: "Minimum time in seconds for a press to count as \"held\"", defaultValue: 1, displayDuringSetup: false)
        }
    }

    tiles(scale: 2) {
        // TODO: define your main and details tiles here
        multiAttributeTile(name:"lastAction", type: "generic", width: 6, height: 4){
            tileAttribute ("device.battery", key: "SECONDARY_CONTROL") {
                attributeState "battery", label:'${currentValue}% battery',icon:"st.Outdoor.outdoor3", unit:"", backgroundColors:[
                [value: 30, color: "#ff0000"],
                [value: 40, color: "#760000"],
                [value: 60, color: "#ff9900"],
                [value: 80, color: "#007600"]
                ]
            }
            tileAttribute ("device.lastAction", key: "PRIMARY_CONTROL") {
                attributeState "active", label:'${currentValue}', icon:"st.Home.home30"
            }

        }
        //        valueTile("lastAction", "device.lastAction", width: 6, height: 2) {
        //			state("lastAction", label:'${currentValue}')
        //		}

        valueTile("battery2", "device.battery", decoration: "flat", inactiveLabel: false, width: 5, height: 1) {
            state("battery", label:'${currentValue}% battery', unit:"")
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        //        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
        //            state "default", label:"bind", action:"configure"
        //        }

    }

    main "lastAction"
    details(["lastAction","battery2","refresh","configure"])

}

// parse events into attributes
def parse(String description) {
	//log.debug "description is $description"
	def event = zigbee.getEvent(description)
    def msg = zigbee.parse(description)
    
    if (shouldProcessMessage(msg)) {
        switch(msg.clusterId) {
            case 0x0001:
            // 0x07 - configure reporting
            if (msg.command != 0x07) {
                def resultMap = [getBatteryResult(msg.data.last())]
                log.debug "$resultMap"
            }
            break

            case 0xFC00:
            if ( msg.command == 0x00 ) {
                def resultMap2 = getButtonResult( msg.data );
                //log.debug "$resultMap"
            }
            break

        }
    }
	
	//log.debug "$msg"
}

private boolean shouldProcessMessage(cluster) {
    // 0x0B is default response indicating message got through
    boolean ignoredMessage = cluster.profileId != 0x0104 ||
    cluster.command == 0x0B ||
    (cluster.data.size() > 0 && cluster.data.first() == 0x3e)
    return !ignoredMessage
}

private List getButtonResult(rawValue) {
    def result
    def button = rawValue[0]
    def buttonState = rawValue[4]
    def buttonHoldTime = rawValue[6]
    def hueStatus = (button as String) + "00" + (buttonState as String) // This is the state in the HUE api
    //log.debug "Button: " + button + "  Hue Code: " + hueStatus + "  Hold Time: " + buttonHoldTime + "  Button State: " + buttonState
    //   result.data = ['buttonNumber': button]

    def buttonName

	// Name of the button
    if ( button == 1 ) { 
        buttonName = "on"
    }
    else if ( button == 2 ) { 
        buttonName = "up" 
    }
    else if ( button == 3 ) {
        buttonName = "down" 
    }
    else if ( button == 4 ) { 
        buttonName = "off" 
    }

	// The button is pressed, aka: pushed + released, with 0 hold time
    if ( buttonState == 0 ) {
         result = "pressed"
    }
	// The button is pressed, aka: pushed + released, with at least 1s hold time
    else if ( buttonState == 2 ) {
       result = "pushed"
    }
	// The button is released, with at least 1s hold time. This code happens after the button is held
    else if ( buttonState == 3 ) {
    	if ( buttonHoldTime >= 8 ) {
        	result = "held2 "+buttonHoldTime
    	} else {
       		result = "held "+buttonHoldTime
        }
    }
	// The button is held
    
    else {
        return
    }
    
    log.debug "Button: " + button + "  Hue Code: " + hueStatus + "  Hold Time: " + buttonHoldTime + "  Button State: " + buttonState + " :: " + result
    //return result

}


private Map getBatteryResult(rawValue) {
    //log.debug "Battery rawValue = ${rawValue}"

    def result = [
    name: 'battery',
    value: '--',
    translatable: true
    ]

    def volts = rawValue / 10

    if (rawValue == 0 || rawValue == 255) {}
    else {
        if (volts > 3.5) {
            result.descriptionText = "{{ device.displayName }} battery has too much power: (> 3.5) volts."
        }
        else {
            if (device.getDataValue("manufacturer") == "SmartThings") {
                volts = rawValue // For the batteryMap to work the key needs to be an int
                def batteryMap = [28:100, 27:100, 26:100, 25:90, 24:90, 23:70,
                22:70, 21:50, 20:50, 19:30, 18:30, 17:15, 16:1, 15:0]
                def minVolts = 15
                def maxVolts = 28

                if (volts < minVolts)
                    volts = minVolts
                else if (volts > maxVolts)
                    volts = maxVolts
                    def pct = batteryMap[volts]
                    if (pct != null) {
                        result.value = pct
                        result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"
                    }
            }
            else {
                def minVolts = 2.1
                def maxVolts = 3.0
                def pct = (volts - minVolts) / (maxVolts - minVolts)
                def roundedPct = Math.round(pct * 100)
                if (roundedPct <= 0)
                    roundedPct = 1
                    result.value = Math.min(100, roundedPct)
                    result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"
            }
        }
    }

    return result
}