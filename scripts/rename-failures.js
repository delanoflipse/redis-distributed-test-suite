// renames runs with errors (e.g. contain a elle folder) so we can easily find them
const { readdir, rename } = require("fs/promises");

const path = require("path");

const STORE_DIR = path.join(
  __dirname,
  "../",
  "jepsen.redis-like",
  "store",
  "redislike"
);
const FAIL_STR = "[FAIL]";
const OK_STR = "[OK]";

async function main() {
  const storeDir = await readdir(STORE_DIR, { withFileTypes: true });
  const folders = storeDir.filter((x) => x.isDirectory());

  await Promise.all(
    folders.map(async (folder) => {
      // ignore previously checked folders
      if (folder.name.includes(OK_STR) || folder.name.includes(FAIL_STR)) {
        return;
      }

      const currentFolderPath = path.join(STORE_DIR, folder.name);

      const insideFolder = await readdir(currentFolderPath, {
        withFileTypes: true,
      });
      
      const elleDir = insideFolder.find(
        (f) => f.isDirectory() && f.name == "elle"
      );
      
      const hasElle = elleDir != null;

      const newFolderPath = path.join(
        STORE_DIR,
        `${folder.name} - ${hasElle ? FAIL_STR : OK_STR}`
      );

      await rename(currentFolderPath, newFolderPath);
    })
  );
}

main();
