package main

import "fmt"
import "os"
import "bufio"
import "io"
import "regexp"
import "strconv"
import "strings"

import "github.com/anishathalye/porcupine"

// read jepsen's history.txt file
func parseJepsenHistory(filename string) []porcupine.Event {
	file, err := os.Open(filename)
	if err != nil {
		panic("can't open file")
	}
	defer file.Close() // executes at the end of function

	reader := bufio.NewReader(file)

	parseLine, _ := regexp.Compile(`^(\d+)\s+:(\w+)\s+:(\w+)\s+(.*?)(\s:(.*?))?$`)
	unwrapArray, _ := regexp.Compile(`^\[\[(.*?)]]$`)
	parseRead, _ := regexp.Compile(`^:r (.*?) (.*?)$`)
	parseAppend, _ := regexp.Compile(`^:append (.*?) (.*?)$`)

	var events []porcupine.Event = nil

	id := 0
	procIdMap := make(map[int]int)
	for {
		lineBytes, isPrefix, err := reader.ReadLine()
		if err == io.EOF {
			break
		} else if err != nil {
			panic("error while reading file: " + err.Error())
		}
		if isPrefix {
			panic("can't handle isPrefix")
		}
		line := string(lineBytes)
		//fmt.Println(line)

		if parseLine.MatchString(line) {
			args := parseLine.FindStringSubmatch(line)
			proc, _ := strconv.Atoi(args[1])
			switch args[2] {
			case "invoke":
				unwraped_commands := strings.Split(unwrapArray.FindStringSubmatch(args[4])[1], "] [")
				fmt.Println(id, proc, "start", args[4], len(unwraped_commands), unwraped_commands)
				procIdMap[proc] = id
				id++
			case "ok":
				fmt.Println(procIdMap[proc], proc, "done", args[4])
				delete(procIdMap, proc)
			case "fail":
				fmt.Println(procIdMap[proc], proc, "failed", args[4])
				delete(procIdMap, proc)
			}
		} else {
			fmt.Println("match failed")
		}
	}

	return events
}