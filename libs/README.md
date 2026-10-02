# libs

This folder is **not** in the repository. It holds one build-time mod jar that the project compiles
against and never ships.

## What goes here

| File | Where to get it |
|---|---|
| `tacz-1.20.1-1.1.8-hotfix.jar` | CurseForge — *Timeless and Classics Zero*, Minecraft 1.20.1, version **1.1.8-hotfix** |

Drop the jar in with exactly that filename, or edit the path in `build.gradle`:

```groovy
compileOnly files("libs/tacz-1.20.1-1.1.8-hotfix.jar")
```

## Why it is not committed

`compileOnly` means the jar is needed to compile and is **never bundled into the output** — the
build is verified clean:

```bash
unzip -l build/libs/mlum-*.jar | grep -cE "com/tacz|atsuishio"   # must print 0
```

So it is somebody else's mod, 55 MB, and redistributing it is not ours to do.

## Why it is needed at all

Most of this mod reads guns straight out of raw NBT precisely so it does not depend on TACZ. One
question cannot be answered that way: **does this gun accept this attachment?** That lives in the
gun's data pack entry, reachable only through `IGun.allowAttachment`. The attachment UI and the
تعشيق أكثر skill's mixin are built on it.

Five files import `com.tacz`:

- `compat/TaczAttachments.java`
- `menu/AttachmentContainer.java`
- `menu/slot/AttachmentSlot.java`
- `mixin/TaczAttachmentMixin.java`
- `client/ui/mc/BagScreen.java`

Superb Warfare, by contrast, has no jar here at all — it is Kotlin with its own runtime, so it is
reached by superclass-name check and reflection in `compat/SbwCompat.java`. That is the pattern to
follow if TACZ ever needs dropping too.
