# 🐛 Complete Bug Audit — react-native-callkeep-advanced

Poori library ka code review kiya hai — JS, TypeScript, Android (Java), iOS (Obj-C) sab files check ki hain.

---

## 🔴 Critical Bugs (App crash ho jayega ya freeze ho jayega)

### BUG 1 — `displayIncomingCall` poora app freeze kar deta hai (Android)

**File:** [RNCallKeepModule.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/RNCallKeepModule.java#L311-L328)

**Kya problem hai:**
`displayIncomingCall` method andar se `listenToNativeCallsState()` call karta hai. Android 12 se neeche (API < 31) wale devices pe ye `Looper.prepare()` + `Looper.loop()` call karta hai. **`Looper.loop()` kabhi return nahi hota** — ye thread ko permanently block kar deta hai. Aur ye React Native bridge thread pe run hota hai, toh **poora app freeze ho jayega**.

```java
// Line 326 — ye line kabhi return nahi hoti!
Looper.loop();
```

> [!CAUTION]
> Ye sabse dangerous bug hai. Android 12 se purane devices pe `displayIncomingCall` ya `startCall` call karne pe **poora JS bridge permanently freeze** ho jayega kyunki `Looper.loop()` kabhi wapas nahi aata.

**Fix kaise karein:** `listenToNativeCallsState()` ko ek alag background `HandlerThread` pe run karo, bridge thread pe nahi.

---

### BUG 2 — `Looper.myLooper().quit()` se NPE crash hota hai (Android)

**File:** [RNCallKeepModule.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/RNCallKeepModule.java#L300-L308)

**Kya problem hai:**
`stopListenToNativeCallsState()` mein line 307 pe:

```java
Looper.myLooper().quit();
```

Agar `Looper.myLooper()` null return kare (jo main thread ya kisi bhi thread pe hoga jisne `Looper.prepare()` nahi kiya), toh **NullPointerException crash** aayega.

**Fix kaise karein:** Null check lagao: `if (Looper.myLooper() != null) Looper.myLooper().quit();`

---

### BUG 3 — `currentConnectionService` null hone pe crash (Android)

**File:** [VoiceConnectionService.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/VoiceConnectionService.java#L225-L236)

**Kya problem hai:**

```java
public static void deinitConnection(String connectionId) {
    VoiceConnectionService.hasOutgoingCall = false;
    currentConnectionService.stopForegroundService(); // ← agar null hai toh NPE crash!
```

`currentConnectionService` null ho sakta hai agar service abhi start nahi hui. Ye `VoiceConnection.reportDisconnect()`, `_onReject()`, `onDisconnect()`, `onAbort()`, aur `IncomingCallActivity.declineCall()` se call hota hai — matlab bahut jagah se crash ho sakta hai.

**Fix kaise karein:** Null check lagao: `if (currentConnectionService != null) currentConnectionService.stopForegroundService();`

---

### BUG 4 — `sendDTMF` empty string pe crash (Android)

**File:** [RNCallKeepModule.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/RNCallKeepModule.java#L926-L936)

**Kya problem hai:**

```java
char dtmf = key.charAt(0); // ← agar key empty ya null hai toh crash!
```

Agar `key` null ya empty string `""` hai, toh `StringIndexOutOfBoundsException` ya `NullPointerException` aayega.

**Fix kaise karein:** Validation lagao: `if (key == null || key.isEmpty()) return;`

---

### BUG 5 — String comparison `==` se ho raha hai `.equals()` ki jagah (Android)

**File:** [VoiceConnectionService.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/VoiceConnectionService.java#L316)

**Kya problem hai:**

```java
if(callUUID == null || callUUID == ""){  // ← ye reference compare karta hai, value nahi!
```

Java mein `==` strings ka reference compare karta hai, value nahi. Toh `callUUID == ""` hamesha `false` aayega chahe string actually empty ho. Matlab empty UUIDs ko random UUID se replace nahi kiya jayega.

**Fix kaise karein:** `if(callUUID == null || callUUID.isEmpty()){`

---

### BUG 6 — `getSelectedAudioRoute` crash hota hai jab outputs empty ho (iOS)

**File:** [RNCallKeep.m](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/ios/RNCallKeep/RNCallKeep.m#L652-L665)

**Kya problem hai:**

```objc
AVAudioSessionPortDescription *selectedOutput = selectedOutputs[0]; // ← agar empty hai toh crash!
```

Koi bounds check nahi hai index 0 access karne se pehle. Agar `selectedOutputs` empty hai, toh **NSRangeException crash** aayega.

**Fix kaise karein:** Check lagao: `if (selectedOutputs == nil || selectedOutputs.count == 0) return nil;`

---

## 🟠 High-Severity Bugs (Galat behavior, events kho jayenge)

### BUG 7 — `_setupIOS` reject ke baad bhi native setup run karta hai (JS)

**File:** [index.js](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/index.js#L327-L337)

**Kya problem hai:**

```javascript
_setupIOS = async (options) =>
    new Promise((resolve, reject) => {
      if (!options.appName) {
        reject('RNCallKeep.setup: option "appName" is required');
      }
      // reject ke baad bhi code chalte rehta hai!
      resolve(RNCallKeepModule.setup(options)); // ← ye bhi execute hoga!
    });
```

`reject()` ke baad execution ruki nahi — `resolve()` bhi call hota hai. Promise ka second settle ignore ho jata hai lekin `RNCallKeepModule.setup(options)` **invalid options ke saath execute ho jayega**.

**Fix kaise karein:** Har `reject()` ke baad `return` lagao.

---

### BUG 8 — `_alert` cancel button pe unhandled promise rejection (JS)

**File:** [index.js](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/index.js#L366-L385)

**Kya problem hai:**

```javascript
{
  text: options.cancelButton,
  onPress: reject,  // ← raw reject bina error object ke
  style: 'cancel',
},
```

Jab user "Cancel" button dabayega Android phone account alert mein, promise reject hoga. Lekin `_setupAndroid` mein `await this._alert(...)` call hai bina try/catch ke. Ye **unhandled promise rejection** create karega jo production builds mein crash kar sakta hai.

**Fix kaise karein:** `reject` ki jagah `resolve(false)` use karo, ya fir `_alert` call ko try/catch mein wrap karo.

---

### BUG 9 — `getCalls()` Android pe `undefined` return karta hai (JS)

**File:** [index.js](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/index.js#L225-L229)

**Kya problem hai:**

```javascript
getCalls = () => {
    if (isIOS) {
      return RNCallKeepModule.getCalls();
    }
    // ← Android pe kuch return nahi hota! undefined milega
  };
```

Android pe ye method kuch bhi return nahi karta. Jo log `getCalls()` use karenge unko unexpected `undefined` milega.

**Fix kaise karein:** Android ke liye `return []` ya `return null` karo with console warning, ya fir Android pe bhi implement karo.

---

### BUG 10 — `hasActiveCall` flag reject pe reset nahi hota (Android)

**File:** [RNCallKeepModule.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/RNCallKeepModule.java#L134)

**Kya problem hai:**
`hasActiveCall` flag `true` set hota hai jab `CALL_STATE_OFFHOOK` detect hota hai. Ye sirf `endCall()` aur `endAllCalls()` mein `false` hota hai. Lekin agar call **reject** hota hai (`rejectCall()`), toh `hasActiveCall` **kabhi reset nahi hota**. Iska matlab baad mein `onHasActiveCall` events galat fire honge.

**Fix kaise karein:** `rejectCall()` method mein bhi `this.hasActiveCall = false;` lagao.

---

### BUG 11 — `IncomingCallActivity.answerCall` Connection ko actually answer nahi karta (Android)

**File:** [IncomingCallActivity.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/IncomingCallActivity.java#L309-L337)

**Kya problem hai:**
`answerCall()` method `ACTION_ANSWER_CALL` broadcast karta hai `LocalBroadcastManager` se, lekin **actual `VoiceConnection` pe `conn.onAnswer()` kabhi call nahi karta**. Matlab:
- System call UI abhi bhi "ringing" dikhayega
- Connection state telecom framework se inconsistent rahega
- 45-second timeout phir bhi fire ho sakta hai aur call auto-reject ho jayega

**Fix kaise karein:** Add karo: `VoiceConnection conn = (VoiceConnection) VoiceConnectionService.getConnection(callUUID); if (conn != null) conn.onAnswer();`

---

### BUG 12 — `ACTION_WAKE_APP` broadcast receiver filter mein missing hai (Android)

**File:** [RNCallKeepModule.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/RNCallKeepModule.java#L1207-L1236)

**Kya problem hai:**
`registerReceiver()` method mein bahut saare actions ka filter add hai, lekin `ACTION_WAKE_APP` ka **nahi hai**. Lekin `VoiceBroadcastReceiver.onReceive()` mein `case ACTION_WAKE_APP:` handler hai (line 1353). Kyunki filter registered nahi hai, ye broadcast **kabhi receive nahi hoga** — matlab background se app wake-up flow kaam nahi karega.

**Fix kaise karein:** `registerReceiver()` mein `intentFilter.addAction(ACTION_WAKE_APP);` add karo.

---

### BUG 13 — `setForegroundServiceSettings` settings ko galat tarike se overwrite karta hai (Android)

**File:** [RNCallKeepModule.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/RNCallKeepModule.java#L975-L988)

**Kya problem hai:**

```java
WritableMap settings = getSettings(null);
settings.putMap("foregroundService", ...);
setSettings(settings); // ← ye static _settings ko directly modify karta hai
```

`getSettings()` se mila `WritableMap` actually static `_settings` ka reference hai. `putMap` call karne se **static _settings directly modify ho jata hai** bina store karne se pehle. Ye in-place mutation se subtle bugs aa sakte hain.

---

### BUG 14 — `backToForeground` mein WindowManager aur Intent flags mix ho rahe hain (Android)

**File:** [RNCallKeepModule.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/RNCallKeepModule.java#L1093-L1096)

**Kya problem hai:**

```java
focusIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK +
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +  // ← ye Window flag hai, Intent flag nahi!
        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD +
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
```

Ye **alag-alag flag domains** (WindowManager vs Intent) ko arithmetic `+` se mix kar raha hai. Ye galat flag values produce karega.

**Fix kaise karein:** Intent flags ke liye `|` (bitwise OR) use karo, aur Window flags ko Activity window pe separately set karo.

---

### BUG 15 — `MapUtils.readableToWritableMap` fail hone pe silently `null` return karta hai (Android)

**File:** [MapUtils.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/MapUtils.java#L66-L76)

**Kya problem hai:**

```java
public static WritableMap readableToWritableMap(ReadableMap readableMap) {
    try {
        // ...
    } catch (JSONException e) {
    }
    return null; // ← chupke se null return kar deta hai!
}
```

Exception silently swallow ho jaata hai aur `null` return hota hai. Callers jaise `storeSettings()` aur `setForegroundServiceSettings()` null check nahi karte, toh baad mein NPE crash ho sakta hai.

---

## 🟡 Medium-Severity Bugs (Edge cases, type mismatches)

### BUG 16 — TypeScript `index.d.ts` mein galat module name hai

**File:** [index.d.ts](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/index.d.ts#L1)

**Kya problem hai:**

```typescript
declare module 'react-native-callkeep' {  // ← galat naam!
```

Package ka naam `react-native-callkeep-advanced` hai, lekin type declaration `react-native-callkeep` bol raha hai. TypeScript users ko **types nahi milenge** jab wo `react-native-callkeep-advanced` import karenge.

**Fix kaise karein:** `declare module 'react-native-callkeep-advanced'` mein change karo.

---

### BUG 17 — TypeScript mein `static` methods hain lekin export singleton hai

**File:** [index.d.ts](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/index.d.ts#L138) vs [index.js](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/index.js#L404)

**Kya problem hai:**
TypeScript declaration mein `RNCallKeep` class ke methods `static` hain, lekin `index.js` **singleton instance** export karta hai (`export default new RNCallKeep()`). Types conceptually galat hain — kaam toh chalega lekin `new RNCallKeep()` TypeScript mein fail hoga.

---

### BUG 18 — `onHasActiveCall` ka event name type mein galat hai

**File:** [index.d.ts](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/index.d.ts#L19) vs [actions.js](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/actions.js#L22)

**Kya problem hai:**

```typescript
// index.d.ts line 19
onHasActiveCall : 'onHasActiveCall';  // ← galat event name
```

Actual native event name `'RNCallKeepHasActiveCall'` hai (actions.js line 22). Type mein `'onHasActiveCall'` likha hai jo match nahi karta.

---

### BUG 20 — `checkIsInManagedCall` iOS pe hamesha `false` return karta hai

**File:** [index.js](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/index.js#L164)

**Kya problem hai:**

```javascript
checkIsInManagedCall = async () => isIOS? false: RNCallKeepModule.checkIsInManagedCall();
```

iOS pe ye hamesha `false` return karta hai actual call state check kiye bina. TypeScript type `Promise<boolean>` kehta hai lekin iOS pe kabhi `true` nahi aayega.

---

### BUG 22 — `declineCall` duplicate `endCall` events JS ko bhejta hai (Android)

**File:** [IncomingCallActivity.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/IncomingCallActivity.java#L340-L358)

**Kya problem hai:**
`declineCall()` method `ACTION_END_CALL` intent create karta hai AUR `conn.onDisconnect()` bhi call karta hai. `onDisconnect()` andar se phir se `ACTION_END_CALL` broadcast karta hai. Toh JS side ko **ek hi call UUID ke liye DO baar** `endCall` event milega.

**Fix kaise karein:** Ya toh intent broadcast karo YA `conn.onDisconnect()` call karo — dono mat karo.

---

### BUG 23 — System UI se answer karne pe `payload` forward nahi hota (Android)

**File:** [VoiceConnection.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/VoiceConnection.java#L381-L397)

**Kya problem hai:**
`sendCallRequestToActivity` method sirf `HashMap<String, String>` bhejta hai — `Bundle` payload nahi bhejta. Toh jab `VoiceBroadcastReceiver` `ACTION_ANSWER_CALL` handle karta hai aur `EXTRA_PAYLOAD` read karta hai (line 1302), wo **null milega** agar call system UI se answer ki gayi (custom `IncomingCallActivity` se nahi).

---

### BUG 27 — iOS pe `_delayedEvents` send hone ke baad clear nahi hota

**File:** [RNCallKeep.m](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/ios/RNCallKeep/RNCallKeep.m#L122-L129)

**Kya problem hai:**

```objc
- (void)startObserving {
    _hasListeners = YES;
    if ([_delayedEvents count] > 0) {
        [self sendEventWithName:RNCallKeepDidLoadWithEvents body:_delayedEvents];
        // ← _delayedEvents clear nahi hota! Events hamesha accumulate hote rahenge
    }
}
```

Delayed events bhej diye jaate hain lekin kabhi clear nahi hote, toh jab bhi listeners re-add honge, purane events phir se send honge.

**Fix kaise karein:** Send karne ke baad add karo: `_delayedEvents = [NSMutableArray array];`

---

## 🟢 Low-Severity Issues (Warnings, minor)

### BUG 24 — Deprecated `PhoneStateListener` warning (Android)

`PhoneStateListener` API 31+ pe deprecated hai. Code sahi se `TelephonyCallback` use karta hai API 31+ ke liye, lekin `LegacyCallStateListener` class compilation warnings dega.

### BUG 25 — Deprecated `FULL_WAKE_LOCK` use ho raha hai (Android)

**File:** [IncomingCallActivity.java](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/android/src/main/java/io/wazo/callkeep/IncomingCallActivity.java#L63)

`PowerManager.FULL_WAKE_LOCK` API 17 se deprecated hai. `FLAG_KEEP_SCREEN_ON` use karna chahiye.

### BUG 26 — iOS `reportUpdatedCall` mein galat format specifier hai

**File:** [RNCallKeep.m](file:///c:/Gaurav/ReactNative/react-native-callkeep-advanced/ios/RNCallKeep/RNCallKeep.m#L1068)

```objc
NSLog(@"[RNCallKeep][reportUpdatedCall] contactIdentifier = %i", contactIdentifier);
//                                                              ^^^ %i integer ke liye hai, NSString* ke liye %@ chahiye!
```

---

## Summary Table

| # | Severity | Platform | Bug Description |
|---|----------|----------|-----------------|
| 1 | 🔴 Critical | Android | `Looper.loop()` bridge thread ko forever block karta hai API < 31 pe |
| 2 | 🔴 Critical | Android | `Looper.myLooper().quit()` NPE crash |
| 3 | 🔴 Critical | Android | `currentConnectionService` null pe NPE crash `deinitConnection` mein |
| 4 | 🔴 Critical | Android | `sendDTMF` empty key pe crash |
| 5 | 🔴 Critical | Android | String `==` comparison `.equals()` ki jagah |
| 6 | 🔴 Critical | iOS | `getSelectedAudioRoute` empty outputs pe crash |
| 7 | 🟠 High | JS | `_setupIOS` reject ke baad bhi native setup execute karta hai |
| 8 | 🟠 High | JS | `_alert` cancel pe unhandled promise rejection |
| 9 | 🟠 High | JS | `getCalls()` Android pe `undefined` return karta hai |
| 10 | 🟠 High | Android | `hasActiveCall` flag reject pe reset nahi hota |
| 11 | 🟠 High | Android | `IncomingCallActivity.answerCall` Connection ko answer nahi karta |
| 12 | 🟠 High | Android | `ACTION_WAKE_APP` broadcast filter mein missing hai |
| 13 | 🟠 High | Android | `setForegroundServiceSettings` static settings ko in-place mutate karta hai |
| 14 | 🟠 High | Android | `backToForeground` WindowManager aur Intent flags mix karta hai |
| 15 | 🟠 High | Android | `MapUtils.readableToWritableMap` silently null return karta hai |
| 16 | 🟡 Medium | TypeScript | `index.d.ts` mein galat module name |
| 17 | 🟡 Medium | TypeScript | Static vs instance method mismatch |
| 18 | 🟡 Medium | TypeScript | `onHasActiveCall` event name galat hai types mein |
| 20 | 🟡 Medium | JS/iOS | `checkIsInManagedCall` iOS pe hamesha false return karta hai |
| 22 | 🟡 Medium | Android | `declineCall` duplicate `endCall` events bhejta hai JS ko |
| 23 | 🟡 Medium | Android | System UI answer pe `payload` forward nahi hota |
| 27 | 🟡 Medium | iOS | `_delayedEvents` send ke baad clear nahi hota |
| 24 | 🟢 Low | Android | Deprecated `PhoneStateListener` warnings |
| 25 | 🟢 Low | Android | Deprecated `FULL_WAKE_LOCK` |
| 26 | 🟢 Low | iOS | NSLog mein galat format specifier `%i` NSString ke liye |

---

## Aapke Liye Questions

> [!IMPORTANT]
> **6 critical bugs** mile hain jo app crash ya freeze karwa sakte hain. Ye next release se pehle fix hone chahiye. Kya main sab bugs fix kar doon?

1. **BUG 1 (Looper.loop):** `listenToNativeCallsState` ko alag `HandlerThread` pe run karein, ya poora native call state monitoring redesign karein?
2. **BUG 11 (IncomingCallActivity answer):** `IncomingCallActivity` mein `conn.onAnswer()` directly call karein, ya `RNCallKeepModule.answerIncomingCall()` ke through route karein?
3. **BUG 16 (module name):** TypeScript declaration mein sirf `react-native-callkeep-advanced` rakhein, ya backward compatibility ke liye dono module names rakhein?
