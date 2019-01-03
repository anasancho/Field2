package fieldgraaljs

class Until
{
    class Stop {}
    class Again {}

    class Wait(val condition: () -> Boolean)

}