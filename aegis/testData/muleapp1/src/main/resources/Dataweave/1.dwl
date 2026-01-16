%dw 2.0 output application/json ---
{
  upperName: upper("rakesh"), // "JOHN DOE"
  splitName: splitBy("apple,banana", ","), // ["apple", "banana"]
  combined: "Hello" ++ " " ++ "World" ++ error.errorType // "Hello World"
}
