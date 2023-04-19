/**
 * Renames runs with errors (e.g. contain a elle folder) so we can easily find them.
 * Requires node v18+.
 * This is clearly a utility script to automate manual inspection.
 * So no, you don't get a package.json, and certainly no support.
 * 
 * And yes, we have scripts in python AND javascript in a clojure project.
 */
const { readdir, rename, readFile, access } = require("fs/promises");

const path = require("path");

const forceFlag = process.argv[2] === "-f";

const STORE_DIR = path.join(
  __dirname,
  "../",
  "store",
  "redislike"
);
const FAIL_STR = "[FAIL]";
const OK_STR = "[OK]";

const FAIL_SEARCH_STR = ":workload {:valid? false";

const dirIsFailedTest = async (folderPath) => {
  const insideFolder = await readdir(folderPath, {
    withFileTypes: true,
  });
  
  // check if there is an "elle" folder.
  const elleDir = insideFolder.find(
    (f) => f.isDirectory() && f.name == "elle"
  );

  if (elleDir != null) {
    return true;
  }

  // check if results.edn contains a failed workload.
  try {
    const resultsFilePath = path.join(folderPath, "results.edn");
    // can throw if file does not not exists
    await access(resultsFilePath);
    const resultString = await readFile(resultsFilePath);
    if (resultString.includes(FAIL_SEARCH_STR)) {
      return true;
    }
  } catch (e) {}

  return false;
}

const databaseForTest = async (folderPath) => {
  // check if results.edn contains a failed workload.
  try {
    const resultsFilePath = path.join(folderPath, "jepsen.log");
    // can throw if file does not not exists
    await access(resultsFilePath);
    const resultString = await readFile(resultsFilePath);
    if (resultString.includes("keydb-server")) {
      return "KEYDB";
    } else {
      return "REDIS";
    }
  } catch (e) {}

  return "???";
}

async function main() {
  const storeDir = await readdir(STORE_DIR, { withFileTypes: true });
  const folders = storeDir.filter((x) => x.isDirectory());

  await Promise.all(
    folders.map(async (folder) => {
      const folderName = folder.name;
      const [originalName, statusString] = folderName.split(" - ");
      // ignore previously checked folders
      if (statusString != null && !forceFlag) {
        return;
      }

      const currentFolderPath = path.join(STORE_DIR, folderName);
      const dbName = await databaseForTest(currentFolderPath);

      const hasFailure = await dirIsFailedTest(currentFolderPath);

      const newFolderPath = path.join(
        STORE_DIR,
        `${originalName} - [${dbName}] ${hasFailure ? FAIL_STR : OK_STR}`
      );

      await rename(currentFolderPath, newFolderPath);
    })
  );
}

main();
