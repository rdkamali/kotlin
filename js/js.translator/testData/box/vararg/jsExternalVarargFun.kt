// TARGET_BACKEND: JS_IR

//KT-42357

// FILE: main.kt
external fun create(
    arg: Int = definedExternally,
    vararg args: String
) : Array<String>

fun box(): String {
    val zeroArgs = create()
    if (zeroArgs.size != 0) return "fail: $zeroArgs arguments"

    val oneArg = create(1)
    if (oneArg.size != 1 || js("typeof oneArg[0] !== 'number'")) return "fail1: $oneArg arguments"

    val varArgs = create(2, "p0", "p1", "p3")
    if (varArgs.size != 4 || js("typeof varArgs[0] !== 'number'") || js("typeof varArgs[1] !== 'string'")) return "fail2: $varArgs arguments"

    val namedParameter = create(3, args = arrayOf("p0", "p1"))
    if (namedParameter.size != 3 || js("typeof varArgs[0] !== 'number'") || js("typeof namedParameter[1] !== 'string'")) return "fail3: $namedParameter arguments"

    val spreadArgs = create(4, *arrayOf("p0", "p1"))
    if (spreadArgs.size != 3 || js("typeof varArgs[0] !== 'number'") || js("typeof spreadArgs[1] !== 'string'")) return "fail4: $spreadArgs arguments"

    return "OK"
}

// FILE: main.js
function create() {
    return arguments
}
