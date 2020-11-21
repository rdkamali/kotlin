// TARGET_BACKEND: JS_IR

//KT-42357

// FILE: main.kt
external class FieldPath(
    arg: Int = definedExternally,
    vararg args: String
)

external val ctorCallArgs: Array<String>

fun box(): String {
    FieldPath()
    if (ctorCallArgs.size != 0) return "fail: $ctorCallArgs arguments"

    FieldPath(1)
    if (ctorCallArgs.size != 1 || js("typeof ctorCallArgs[0] !== 'number'")) return "fail1: $ctorCallArgs arguments"

    FieldPath(2, "p0", "p1", "p3")
    if (ctorCallArgs.size != 4 || js("typeof ctorCallArgs[0] !== 'number'") || js("typeof ctorCallArgs[1] !== 'string'"))
        return "fail2: $ctorCallArgs arguments"

    FieldPath(3, args = arrayOf("p0", "p1"))
    if (ctorCallArgs.size != 3 || js("typeof ctorCallArgs[0] !== 'number'") || js("typeof ctorCallArgs[1] !== 'string'"))
        return "fail3: $ctorCallArgs arguments"

    FieldPath(4, *arrayOf("p0", "p1"))
    if (ctorCallArgs.size != 3 || js("typeof ctorCallArgs[0] !== 'number'") || js("typeof ctorCallArgs[1] !== 'string'"))
        return "fail4: $ctorCallArgs arguments"

    return "OK"
}

// FILE: main.js
var ctorCallArgs;
function FieldPath() {
    ctorCallArgs = arguments;
}