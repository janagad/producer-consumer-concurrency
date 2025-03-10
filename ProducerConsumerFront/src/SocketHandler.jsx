import SockJS from "sockjs-client/dist/sockjs";
import { Stomp } from "@stomp/stompjs";

if (typeof global === "undefined") {
  window.global = window;
}

class SocketHandler {
    constructor(
        updateNodes, 
        graphDTO, 
        handleSimulate,
        handleReplay,
        handlePause,
        handleResume,
        handleEnd,
        handleMessage
    ) {
        this.updateNodes = updateNodes
        this.graphDTO = graphDTO
        this.client = null
        this.handleSimulate = handleSimulate
        this.handleReplay = handleReplay
        this.handlePause = handlePause
        this.handleResume = handleResume
        this.handleEnd = handleEnd
        this.handleMessage = handleMessage
    }

    async initiateWebSocket() {
        const socket = new SockJS('http://localhost:8080/ws');
        this.client = Stomp.over(socket);
        this.client.debug = () => {};
        
        return new Promise((resolve, reject) => {
            this.client.connect({}, (frame) => {
                console.log('Connected: ' + frame);
                this.client.subscribe('/topic/main', async (message) => {
                    const jsonData = await JSON.parse(message.body);
                    this.updateNodes(jsonData);
                });
                this.client.subscribe('/topic/status', (message) => {
                    const jsonData = JSON.parse(message.body);
                    console.log(jsonData.action)
                    switch (jsonData.action) {
                        case "start":
                            this.handleSimulate()
                            this.handleMessage("Simulation started")
                            break;
                        case "replay":
                            this.handleReplay()
                            this.handleMessage("Replaying simulation")
                            break;
                        case "pause":
                            this.handlePause()
                            this.handleMessage("Simulation paused")
                            break;
                        case "resume":
                            this.handleResume()
                            this.handleMessage("Simulation resumed")
                            break;
                        case "terminate":
                        case "end":
                            this.handleEnd()
                            this.handleMessage("Simulation ended")
                            break;
                        default:
                            this.handleMessage(jsonData.action)
                    }
                })
                resolve();
            }, (error) => {
                console.error('Error connecting to WebSocket: ', error);
                reject(error);
            });
        });
    }

    terminateWebSocket() {
        this.client.disconnect()
    }

    sendMessage(action) {
        this.client.send('/app/action', {}, action);
        console.log(`Sent message: ${action}`);
    }

    async setGraph() {
        console.log(this.graphDTO)
        try {
            const response = await fetch('http://localhost:8080/setGraph', {
                method: 'POST',
                headers: {
                'Content-Type': 'application/json',
                },
                body: JSON.stringify(this.graphDTO)
            });
        
            if (response.ok) {
                const result = await response.text();
                console.log(result);
                if (result == "Invalid Graph"){
                    this.handleMessage("Invalid Graph");
                    return false;
                }
                return true;
            }
        } 
        catch (error) {
            console.error('Network error:', error);
        }
        return false;
    }

    async startSimulation() {
        if (await this.setGraph() == false)
            return;
        if (this.client == null)
            await this.initiateWebSocket()
        this.sendMessage('start')
    }

    endSimulation() {
        this.sendMessage('terminate')
    }

    pauseSimulation() {
        this.sendMessage('pause')
    }

    resumeSimulation() {
        this.sendMessage('resume')
    }

    replaySimulation() {
        this.sendMessage('replay')
    }
}

export default SocketHandler