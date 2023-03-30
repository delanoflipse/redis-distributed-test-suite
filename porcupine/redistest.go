package main

import "fmt"
import "io/ioutil"

import "github.com/anishathalye/porcupine"


/*type registerState struct {
	value int
}*/


func main() {
    fmt.Println("Hello, World!")

	//parseJepsenHistory("example_history.txt")
	
	events := getKeyValueExampleEvents() //getAppendExampleEvents()
	registerModel := getKeyValueModel() //getAppendModel()
	
	//ok := porcupine.CheckEvents(registerModel, events)
	//fmt.Println(ok)
	
	res, info := porcupine.CheckEventsVerbose(registerModel, events, 0)
	fmt.Println(res)
	
	// create visualization
	file, err := ioutil.TempFile("", "*.html")
	if err != nil {
		fmt.Println("failed to create temp file")
	}
	err = porcupine.Visualize(registerModel, info, file)
	if err != nil {
		fmt.Println("visualization failed")
	}
	fmt.Println("wrote visualization to %s", file.Name())
	
}
