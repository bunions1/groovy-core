package gls.syntax

import static Container.*
import static Ingredient.*
import static CookingAction.*
import static Temperature.*

/**
 * Test case for GEP-3 new features
 *
 * Simple table presenting what is possible and what is not
 * according to this table old syntax should works the same as now
 *
 * expression           |   possible meanings       | allowed in old syntax
 *  foo {c}             |       foo({c})            |  (same meaning)
 *  foo a1              |       foo(a1)             |  (same meaning)
 *  foo a1()            |       foo(a1())           |  (same meaning)
 *  foo a1 {c}          |       foo(a1({c}))        |  (same meaning)
 *  foo a1 a2           |       not allowed         |   not allowed
 *  foo a1() a2         |       not allowed         |   not allowed
 *  foo a1 a2()         |       not allowed         |   not allowed
 *  foo a1 a2 {c}       |       not allowed         |   not allowed
 *  foo a1 {c} a2       |       not allowed         |   not allowed
 *  foo a1 {c} a2 {c}   |       not allowed         |   not allowed
 *  foo a1 a2 a3        |       foo(a1).a2(a3)      |   not allowed
 *  foo a1() a2 a3()    |       foo(a1()).a2(a3())  |   not allowed
 *  foo a1 a2() a3      |       not allowed         |   not allowed
 *  foo a1 a2 a3 {c}    |       foo(a1).a2(a3({c})) |   not allowed
 *  foo a1 a2 a3 a4     |       not allowed         |   not allowed
 *  foo a1 a2 a3 a4 {c} |       not allowed         |   not allowed
 *
 * Summary of the pattern
 * - A command-expression is composed of an even number of elements
 * - The elements are alternating a method name, and its parameters
 *  (can be named and non-named parameters)
 * - A parameter element can be any kind of expression (ie. a method
 *  call foo(), foo{}, or some expression like x+y)
 * - All those pairs of method name and parameters are actually chained
 *  method calls (ie. send "hello" to "Guillaume" is two methods chained
 *  one after the other as send("hello").to("Guillaume"))
 * - extend command expressions to be allowed on the RHS of assignments.
 *  def txt = foo a1() not allowed rigth now
 *
 * @author Lidia Donajczyk-Lipinska
 * @author Jochen "blackdrag" Theodorou
 * @author Guillaume Laforge
 */
class Gep3Test extends GroovyTestCase {

    protected void tearDown() {
        Number.metaClass = null
        Integer.metaClass = null
    }

    static String txt = "Lidia is Groovy ;)"

    void testSimpleClassicalCommandExpressions() {
        foo txt
        foo a1()
        foo a2{}
        foo a2{}, and: txt
    }

    static void foo(a) { assert a == txt }
    static void foo(Map m, a) { assert a == txt && m.and == txt }
    static a1() { return txt }
    static a2(Closure c) { return txt }

    void testNewSyntax() {
        def expectedResult = "from:Lidia;to:Guillaume;body:how are you?;"
        def e = new Email()
        e.from "Lidia" to "Guillaume" body "how are you?"
        def result = e.send()

        assert expectedResult == result
    }

    void testContactInfo() {
        def contact = [name: { String name -> assert name == "Guillaume"; [age: { int age -> assert age == 33 }]}]
        contact.name "Guillaume" age 33
    }

    void testArtistPaintingWithAMixOfNamedAndNonNamedParams() {
        Number.metaClass.getPm = { -> "$delegate PM" }

        def artist = [
            paint: { String what ->
                assert what == "wall"
                [
                    with: { Map m, String c1, String c2 ->
                        assert m.and == "Blue"
                        assert c1 == "Red"
                        assert c2 == "Green"
                        [
                            at: { String time ->
                                assert time == "3 PM"
                            }
                        ]
                    }
                ]
            }
        ]

        artist.paint "wall" with "Red", "Green", and: "Blue" at 3.pm
    }

    void testArgWith() {
        def arr = ["he", "ll", "o"]
        def concat = { String s1 -> [with: { String s2 -> [and: { String s3 -> assert s1+s2+s3 == "hello"}]}]}

        concat arr[0] with arr[1] and arr[2]
    }

    void testWaitAndExecuteUsingParamsTakingClosureAsArg() {
        Number.metaClass.getSecond { -> delegate * 1000 }
        def wait = { int t -> [and: { Closure c -> c() }]}

        wait 1.second and execute { assert true }
    }
    static execute(Closure c) { c }


    static String message = ""
    static drugQuantity, drug

    void testMedicine() {
        Number.metaClass.getPills = { -> new DrugQuantity(q: delegate, form: "pills") }
        Number.metaClass.getHours = { -> new Duration(q: delegate, unit: "hours") }

        def chloroquinine = new Drug(name: "Chloroquinine")

        take 3.pills of chloroquinine after 6.hours

        assert message == "Take 3 pills of Chloroquinine after 6 hours"

    }

    def take(DrugQuantity dq) { drugQuantity = dq; this }
    def of(Drug d) { drug = d; this }
    def after(Duration dur) { message = "Take $drugQuantity of $drug after $dur" }

    void testRecipeDsl() {
        def (once, twice) = [1, 2]

        Integer.metaClass.getMinutes { delegate }

        Recipe.instructions {
            take medium_bowl
            combine soy_sauce, vinegar, chili_powder, garlic
            place chicken into sauce
            turn once to coat
            marinate 30.minutes at room_temperature
        }
    }

    void testExtendedCommandExpressionSpanningTwoLinesWithNewlineAfterNamedArg() {
        boolean success = false
        def good = true
        def margherita = [tastes: { boolean b -> success = true }]
        def check = { Map m -> margherita }

        check that:
                margherita tastes good

        assert success
    }

    def check(Map m) { m.that }

    void testExtendedCommandExpressionsOnTheRHS() {
        def ( coffee,   sugar,   milk,   liquor ) =
            ["coffee", "sugar", "milk", "liquor"]
        def drink = Drink.&drink

        def r1 = drink coffee
        assert r1.beverage == coffee && !r1.ingredients

        def r2 = drink coffee with sugar
        assert r2.beverage == coffee && r2.ingredients == [sugar]

        def r3 = drink coffee with sugar, milk
        assert r3.beverage == coffee && r3.ingredients == [sugar, milk]

        r3 = drink coffee with sugar, milk and liquor
        assert r3.beverage == coffee && r3.ingredients == [sugar, milk, liquor]
    }
}


class Drink {
    String beverage
    List<String> ingredients = []

    static Drink drink(String beverage) {
        new Drink(beverage: beverage)
    }

    def with(String... ingredients) {
        this.ingredients = ingredients.toList()
        return this
    }

    def and(String ingredient) {
        this.ingredients << ingredient
        return this
    }
}

enum Container { medium_bowl }
enum Ingredient { soy_sauce, vinegar, chili_powder, garlic, chicken, sauce }
enum CookingAction { coat }
enum Temperature { room_temperature }

class Recipe {
    static instructions(Closure c) {
        def clone = c.clone()
        clone.delegate = new Recipe()
        clone.resolveStrategy = Closure.DELEGATE_ONLY
        clone()
    }

    void take(Container cont) {
        assert cont == medium_bowl
    }

    void combine(Ingredient... ingr) {
        assert ingr.every { it in Ingredient.values() }
    }

    def place(Ingredient ingr) {
        assert ingr == Ingredient.chicken
        [into: { Ingredient otherIngr -> assert otherIngr == Ingredient.sauce}]
    }

    def turn(Integer i) {
        assert i == 1
        [to: { CookingAction cAct -> assert cAct == CookingAction.coat }]
    }

    def marinate(Integer minutes) {
        assert minutes == 30
        [at: { Temperature temp -> assert temp == Temperature.room_temperature }]
    }
}

class Drug {
    String name
    String toString() { name }
}

class DrugQuantity {
    Number q
    String form
    String toString() { "$q $form" }
}

class Duration {
    Number q
    String unit
    String toString() { "$q $unit" }
}

class Email {
    String msg = ""

    def from(address) {
        msg += "from:" + address + ";"
        return this
    }

    def to(address) {
        msg += "to:" + address + ";"
        return this
    }

    def body(text) {
        msg += "body:" + text + ";"
        return this
    }

    def send() {
        return msg
    }
}
