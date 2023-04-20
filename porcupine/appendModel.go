package main

import "fmt"

import "github.com/anishathalye/porcupine"

type registerInput struct {
    op    bool // false = put/append, true = get/read
    value int
}

func arrayEquals(a []int, b []int) bool {
	if len(a) == len(b) {
		length := len(a)
		for i := 0; i < length; i++ {
			if a[i] != b[i] {
				return false
			}
		}
		return true
	} else {
		return false
	}
}

func getAppendModel() porcupine.Model {
	return porcupine.Model{
		Init: func() interface{} {
			return []int{}
		},
		// step function: takes a state, input, and output, and returns whether it
		// was a legal operation, along with a new state
		Step: func(state, input, output interface{}) (bool, interface{}) {
			regInput := input.(registerInput)
			outv := output.([]int)
			statev := state.([]int)
			if regInput.op == false {
				return true, append(statev, regInput.value) // always ok to execute a put
			} else {
				readCorrectValue := arrayEquals(outv, statev)
				return readCorrectValue, state // state is unchanged
			}
		},

		// Equality on states. If left nil, this package will use == as a
		// fallback ([ShallowEqual]).
		Equal: func(state1, state2 interface{}) bool {
			state1v := state1.([]int)
			state2v := state2.([]int)
			return arrayEquals(state1v, state2v)
		},

		// For visualization, describe an operation as a string. For example,
		// "Get('x') -> 'y'". Can be omitted if you're not producing
		// visualizations.
		DescribeOperation: func(input interface{}, output interface{}) string {
			inputv := input.(registerInput)
			outv := output.([]int)
			if inputv.op { //read
				return fmt.Sprintf("read: %v", outv)
			} else { // write
				return fmt.Sprintf("append: %v", inputv.value)
			}
			return fmt.Sprintf("%#v -> %#v", input, output)
		},
	}
}

func getAppendExampleEvents() []porcupine.Event {
	return []porcupine.Event{
		// C0: put('100')
		{Kind: porcupine.CallEvent, Value: registerInput{false, 100}, Id: 0, ClientId: 0},
		// C1: get()
		{Kind: porcupine.CallEvent, Value: registerInput{true, 0}, Id: 1, ClientId: 1},
		// C2: get()
		{Kind: porcupine.CallEvent, Value: registerInput{true, 0}, Id: 2, ClientId: 2},
		// C2: Completed get() -> '0'
		{Kind: porcupine.ReturnEvent, Value: []int{}, Id: 2, ClientId: 2},
		// C1: Completed get() -> '100'
		{Kind: porcupine.ReturnEvent, Value: []int{100}, Id: 1, ClientId: 1},
		// C0: Completed put('100')
		{Kind: porcupine.ReturnEvent, Value: []int{}, Id: 0, ClientId: 0},

		// C3: get()
		{Kind: porcupine.CallEvent, Value: registerInput{true, 0}, Id: 3, ClientId: 2},
		// C4: put('200')
		{Kind: porcupine.CallEvent, Value: registerInput{false, 200}, Id: 4, ClientId: 0},
		// C2: Completed get() -> '100, 200'
		{Kind: porcupine.ReturnEvent, Value: []int{100, 200}, Id: 3, ClientId: 2},
		// C4: Completed put('200')
		{Kind: porcupine.ReturnEvent, Value: []int{}, Id: 4, ClientId: 0},
	}
} 
