package main

import "fmt"
import "sort"

import "github.com/anishathalye/porcupine"

// used https://github.com/anishathalye/porcupine/blob/v0.1.5/porcupine_test.go as starting point for the kvModel

type kvInput struct {
	op    uint8 // 0 => get, 1 => put, 2 => append
	key   string
	value string
}

type kvOutput struct {
	value string
}

func getKeyValueModel() porcupine.Model {
	return porcupine.Model{
		Partition: func(history []porcupine.Operation) [][]porcupine.Operation {
			m := make(map[string][]porcupine.Operation)
			for _, v := range history {
				key := v.Input.(kvInput).key
				m[key] = append(m[key], v)
			}
			keys := make([]string, 0, len(m))
			for k := range m {
				keys = append(keys, k)
			}
			sort.Strings(keys)
			ret := make([][]porcupine.Operation, 0, len(keys))
			for _, k := range keys {
				ret = append(ret, m[k])
			}
			return ret
		},
		PartitionEvent: func(history []porcupine.Event) [][]porcupine.Event {
			m := make(map[string][]porcupine.Event)
			match := make(map[int]string) // id -> key
			for _, v := range history {
				if v.Kind == porcupine.CallEvent {
					key := v.Value.(kvInput).key
					m[key] = append(m[key], v)
					match[v.Id] = key
				} else {
					key := match[v.Id]
					m[key] = append(m[key], v)
				}
			}
			var ret [][]porcupine.Event
			for _, v := range m {
				ret = append(ret, v)
			}
			return ret
		},
		Init: func() interface{} {
			// note: we are modeling a single key's value here;
			// we're partitioning by key, so this is okay
			return ""
		},
		Step: func(state, input, output interface{}) (bool, interface{}) {
			inp := input.(kvInput)
			out := output.(kvOutput)
			st := state.(string)
			if inp.op == 0 {
				// get
				return out.value == st, state
			} else if inp.op == 1 {
				// put
				return true, inp.value
			} else {
				// append
				return true, (st + inp.value)
			}
		},
		DescribeOperation: func(input, output interface{}) string {
			inp := input.(kvInput)
			out := output.(kvOutput)
			switch inp.op {
			case 0:
				return fmt.Sprintf("get('%s') -> '%s'", inp.key, out.value)
			case 1:
				return fmt.Sprintf("put('%s', '%s')", inp.key, inp.value)
			case 2:
				return fmt.Sprintf("append('%s', '%s')", inp.key, inp.value)
			default:
				return "<invalid>"
			}
		},
	}
}

func getKeyValueExampleEvents() []porcupine.Event {
	return []porcupine.Event{
		// C0: put(x=y)
		{Kind: porcupine.CallEvent, Value: kvInput{op: 1, key: "x", value: "y"}, Id: 0, ClientId: 0},


		// C1: get(x)
		{Kind: porcupine.CallEvent, Value: kvInput{op: 0, key: "x"}, Id: 1, ClientId: 1},
		// C1: Completed get(x) -> y
		{Kind: porcupine.ReturnEvent, Value: kvOutput{"y"}, Id: 1, ClientId: 1},

		// C1: get(x)
		{Kind: porcupine.CallEvent, Value: kvInput{op: 0, key: "y"}, Id: 2, ClientId: 1},

		// C0: put(x=y)
		{Kind: porcupine.CallEvent, Value: kvInput{op: 1, key: "y", value: "5"}, Id: 3, ClientId: 2},

		// C1: Completed get(x) -> y
		{Kind: porcupine.ReturnEvent, Value: kvOutput{"5"}, Id: 2, ClientId: 1},

		// C0: Completed put(x=y)
		{Kind: porcupine.ReturnEvent, Value: kvOutput{}, Id: 3, ClientId: 2},

		// C0: Completed put(x=y)
		{Kind: porcupine.ReturnEvent, Value: kvOutput{}, Id: 0, ClientId: 0},
	}
}
