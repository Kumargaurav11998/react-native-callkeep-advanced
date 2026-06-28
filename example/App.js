import React, { useState, useEffect } from 'react';
import {
  Platform,
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  ScrollView,
  PermissionsAndroid,
  SafeAreaView,
  StatusBar,
} from 'react-native';
// Removed uuid import due to crypto ReferenceError
import RNCallKeep from 'react-native-callkeep-advanced';
import BackgroundTimer from 'react-native-background-timer';

const isIOS = Platform.OS === 'ios';
const hitSlop = { top: 10, left: 10, right: 10, bottom: 10 };

// Modern dark-mode color palette
const theme = {
  background: '#0F172A',
  card: '#1E293B',
  primary: '#3B82F6',
  success: '#10B981',
  danger: '#EF4444',
  text: '#F8FAFC',
  textSecondary: '#94A3B8',
};

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: theme.background,
  },
  container: {
    flex: 1,
    padding: 20,
  },
  header: {
    fontSize: 28,
    fontWeight: 'bold',
    color: theme.text,
    marginBottom: 10,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    color: theme.textSecondary,
    marginBottom: 30,
    textAlign: 'center',
  },
  card: {
    backgroundColor: theme.card,
    borderRadius: 16,
    padding: 20,
    marginBottom: 20,
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  button: {
    backgroundColor: theme.primary,
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    marginBottom: 12,
  },
  buttonSuccess: {
    backgroundColor: theme.success,
  },
  buttonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600',
  },
  logContainer: {
    flex: 1,
    backgroundColor: '#000',
    borderRadius: 12,
    padding: 15,
    marginTop: 10,
  },
  logText: {
    color: '#10B981',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 12,
    lineHeight: 18,
  },
  payloadBox: {
    backgroundColor: 'rgba(16, 185, 129, 0.1)',
    borderWidth: 1,
    borderColor: theme.success,
    borderRadius: 8,
    padding: 12,
    marginBottom: 15,
  },
  payloadTitle: {
    color: theme.success,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  payloadText: {
    color: theme.text,
    fontSize: 14,
  },
  callControls: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    marginTop: 20,
  },
  controlButton: {
    width: '48%',
    backgroundColor: '#334155',
    borderRadius: 12,
    padding: 15,
    alignItems: 'center',
    marginBottom: 15,
  },
  controlButtonActive: {
    backgroundColor: theme.primary,
  },
  controlButtonDanger: {
    width: '100%',
    backgroundColor: theme.danger,
    marginTop: 10,
  },
  controlButtonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600',
  },
});

RNCallKeep.setup({
  ios: {
    appName: 'CallKeepDemo',
  },
  android: {
    alertTitle: 'Permissions required',
    alertDescription: 'This application needs to access your phone accounts',
    cancelButton: 'Cancel',
    okButton: 'ok',
    selfManaged: true,
    foregroundService: {
      channelId: 'com.example.callkeep',
      channelName: 'PulseSync Calls',
      notificationTitle: 'Incoming Call',
      notificationIcon: 'ic_launcher'
    },
  },
});

export default function App() {
  const [logs, setLogs] = useState([]);
  const [lastPayload, setLastPayload] = useState(null);
  
  // Active Call State
  const [activeCallUUID, setActiveCallUUID] = useState(null);
  const [isMuted, setIsMuted] = useState(false);
  const [isOnHold, setIsOnHold] = useState(false);
  const [isSpeaker, setIsSpeaker] = useState(false);

  const log = (text) => {
    console.info(text);
    setLogs((prev) => [`[${new Date().toLocaleTimeString()}] ${text}`, ...prev]);
  };

  useEffect(() => {
    const requestPermissions = async () => {
      if (Platform.OS === 'android') {
        try {
          const permissionsToRequest = [];
          
          if (Platform.Version >= 33) {
            permissionsToRequest.push(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
          }
          if (Platform.Version >= 30) {
            permissionsToRequest.push(PermissionsAndroid.PERMISSIONS.READ_PHONE_NUMBERS);
          }
          
          if (permissionsToRequest.length > 0) {
            const granted = await PermissionsAndroid.requestMultiple(permissionsToRequest);
            log(`Permissions granted: ${JSON.stringify(granted)}`);
          }
        } catch (err) {
          console.warn(err);
        }
      }
    };
    requestPermissions();

    RNCallKeep.addEventListener('answerCall', handleAnswerCall);
    RNCallKeep.addEventListener('endCall', handleEndCall);
    RNCallKeep.addEventListener('didDisplayIncomingCall', handleDisplayCall);
    RNCallKeep.addEventListener('showIncomingCallUi', handleShowIncomingCallUi);
    
    // New Listeners for Hardware/OS Events
    RNCallKeep.addEventListener('didPerformSetMutedCallAction', handleMuteAction);
    RNCallKeep.addEventListener('didToggleHoldCallAction', handleHoldAction);
    RNCallKeep.addEventListener('didPerformDTMFAction', handleDTMFAction);
    RNCallKeep.addEventListener('didChangeAudioRoute', handleAudioRouteAction);

    return () => {
      RNCallKeep.removeEventListener('answerCall', handleAnswerCall);
      RNCallKeep.removeEventListener('endCall', handleEndCall);
      RNCallKeep.removeEventListener('didDisplayIncomingCall', handleDisplayCall);
      RNCallKeep.removeEventListener('showIncomingCallUi', handleShowIncomingCallUi);
      
      RNCallKeep.removeEventListener('didPerformSetMutedCallAction', handleMuteAction);
      RNCallKeep.removeEventListener('didToggleHoldCallAction', handleHoldAction);
      RNCallKeep.removeEventListener('didPerformDTMFAction', handleDTMFAction);
      RNCallKeep.removeEventListener('didChangeAudioRoute', handleAudioRouteAction);
    };
  }, []);

  // Hardware/OS Listener Handlers
  const handleMuteAction = ({ muted, callUUID }) => {
    log(`[Mute Action] System requested mute: ${muted} for call: ${callUUID.split('-')[0]}`);
    setIsMuted(muted);
  };

  const handleHoldAction = ({ hold, callUUID }) => {
    log(`[Hold Action] System requested hold: ${hold} for call: ${callUUID.split('-')[0]}`);
    setIsOnHold(hold);
  };

  const handleDTMFAction = ({ digits, callUUID }) => {
    log(`[DTMF Action] System requested DTMF: ${digits} for call: ${callUUID.split('-')[0]}`);
  };

  const handleAudioRouteAction = ({ output }) => {
    log(`[Audio Route] System changed audio route to: ${output}`);
    setIsSpeaker(output === 'Speaker');
  };

  const handleAnswerCall = (event) => {
    const { callUUID, payload } = event;
    log(`Answered call: ${callUUID.split('-')[0]}`);
    if (payload) {
      log(`Answer Payload: ${JSON.stringify(payload)}`);
      setLastPayload(payload);
    }
    RNCallKeep.setCurrentCallActive(callUUID);
    setActiveCallUUID(callUUID); // Show active call UI
  };

  const handleEndCall = (event) => {
    const { callUUID } = event;
    log(`Ended/Declined call: ${callUUID.split('-')[0]}`);
    setActiveCallUUID(null);
  };

  const handleDisplayCall = (event) => {
    const { callUUID, payload } = event;
    log(`[didDisplayIncomingCall] callUUID: ${callUUID.split('-')[0]} - Full event: ${JSON.stringify(event)}`);
    if (payload) {
      log(`Display Payload: ${JSON.stringify(payload)}`);
      setLastPayload(payload);
    }
  };

  const handleShowIncomingCallUi = (event) => {
    const { callUUID } = event;
    log(`[showIncomingCallUi] callUUID: ${callUUID.split('-')[0]} - Full event: ${JSON.stringify(event)}`);
  };

  const triggerCall = (delay = 0) => {
    // RNCallKeep.endAllCalls(); // Clear any previous stuck calls
    const trigger = () => {
      const callUUID = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
      });
      const number = String(Math.floor(Math.random() * 100000));
      log(`Triggering incoming call... ${number}`);
      
      RNCallKeep.displayIncomingCall(callUUID, number, "Kumar Gaurav", 'number', true, {
        android: {
          backgroundColor: '#2563EB',
           avatarUrl: 'https://i.pravatar.cc/300',
          payload: {
            roomId: `room_${number}`,
            name :"Gaurav",
            extradata:"xddx"
          }
        }
      });
    };

    if (delay > 0) {
      log(`Call scheduled in ${delay / 1000} seconds`);
      BackgroundTimer.setTimeout(trigger, delay);
    } else {
      trigger();
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor={theme.background} />
      <View style={styles.container}>
        <Text style={styles.header}>Gaurav's Call Management System</Text>
        <Text style={styles.subtitle}>Advanced Call Management System</Text>

        {!activeCallUUID ? (
          <View style={styles.card}>
            <TouchableOpacity 
              style={styles.button} 
              onPress={() => triggerCall(0)}
              hitSlop={hitSlop}
            >
              <Text style={styles.buttonText}>Simulate Call Now</Text>
            </TouchableOpacity>

            <TouchableOpacity 
              style={[styles.button, styles.buttonSuccess]} 
              onPress={() => triggerCall(3000)}
              hitSlop={hitSlop}
            >
              <Text style={styles.buttonText}>Schedule Call (3s Delay)</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View style={styles.card}>
            
            <View style={styles.callControls}>
              <TouchableOpacity 
                style={[styles.controlButton, isMuted && styles.controlButtonActive]} 
                onPress={() => {
                  RNCallKeep.setMutedCall(activeCallUUID, !isMuted);
                  setIsMuted(!isMuted);
                  log(`Toggled Mute to ${!isMuted}`);
                }}
              >
                <Text style={styles.controlButtonText}>{isMuted ? 'Unmute' : 'Mute'}</Text>
              </TouchableOpacity>

              <TouchableOpacity 
                style={[styles.controlButton, isOnHold && styles.controlButtonActive]} 
                onPress={() => {
                  RNCallKeep.setOnHold(activeCallUUID, !isOnHold);
                  setIsOnHold(!isOnHold);
                  log(`Toggled Hold to ${!isOnHold}`);
                }}
              >
                <Text style={styles.controlButtonText}>{isOnHold ? 'Resume' : 'Hold'}</Text>
              </TouchableOpacity>

              <TouchableOpacity 
                style={[styles.controlButton, isSpeaker && styles.controlButtonActive]} 
                onPress={() => {
                  if (Platform.OS === 'android') {
                    RNCallKeep.toggleAudioRouteSpeaker(activeCallUUID, !isSpeaker);
                  }
                  setIsSpeaker(!isSpeaker);
                  log(`Toggled Speaker to ${!isSpeaker}`);
                }}
              >
                <Text style={styles.controlButtonText}>{isSpeaker ? 'Phone' : 'Speaker'}</Text>
              </TouchableOpacity>

              <TouchableOpacity 
                style={styles.controlButton} 
                onPress={() => {
                  RNCallKeep.sendDTMF(activeCallUUID, '1');
                  log(`Sent DTMF tone: 1`);
                }}
              >
                <Text style={styles.controlButtonText}>Dial "1"</Text>
              </TouchableOpacity>

                
            </View>
          </View>
        )}

     

        <View style={styles.logContainer}>
          <ScrollView>
            {logs.map((logStr, index) => (
              <Text key={index} style={styles.logText}>{logStr}</Text>
            ))}
          </ScrollView>
        </View>
      </View>
    </SafeAreaView>
  );
}
