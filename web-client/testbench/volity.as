// Flash-to-JavaScript bridge functions for Volity.
// These do little more than call JavaScript functions defined in volity.js.
// In other words, it's a bunch of convenience functions.
// This does implement the entire Volity ECMAScript API, though.

import flash.external.ExternalInterface;

private function rpc(rpc_method:String, ...rest):void {
    ExternalInterface.call("rpc", rpc_method, rest);
}

private function seatmark(...rest):void {
    ExternalInterface.call("seatmark", rest);
}

private function literalmessage(...rest):void {
    ExternalInterface.call("literalmessage", rest);
}

private function message(...rest):void {
    ExternalInterface.call("message", rest);
}

private function localize(...rest):String {
    return ExternalInterface.call("localize", rest);
}

// Flash UIs probably won't call this one much, but whatevs.
private function audio(...rest):void {
    ExternalInterface.call("audio", rest);
}

