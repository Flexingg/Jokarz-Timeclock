# Tasker profile XML — the schema this app emits

This document exists because the pre-2.8.1 export was hand-written XML that Tasker rejected with

```
Import failed. Please make sure to update to the latest Tasker version. You may need to sign up for the beta.
Error details: Missing event type
```

Nobody should have to rediscover the format. Everything below is either quoted from a
known-good Tasker export or from a published code table, and each claim names its source.

## Sources

| # | Source | Used for |
|---|--------|----------|
| S1 | [`Taskomater/Tasker-XML-Info`](https://github.com/Taskomater/Tasker-XML-Info) — `README.md` (file types, node/tag reference) and `Tasker_XML_Codes.md` (numeric code tables, generated from Tasker v5.15.5-beta) | the node/tag skeleton and the numeric codes |
| S2 | [`ankidroid/apisample`](https://github.com/ankidroid/apisample) → `AnkiDroid_Sync.prf.xml` — a real `.prf.xml` shipped by the AnkiDroid project, `tv="4.7u3m"` | a real **Profile file**: `<Profile>` + linked `<Task>`, an **Intent Received** trigger, and a **Send Intent** action |
| S3 | [`FerSaiyan/Alternative-HeyCyan-App-and-SDK`](https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK) → `android/CyanBridge/tasker/CyanBridge_HIL_Tasker.prj.xml`, `tv="6.5.11"` (a recent Tasker) | confirms S2's Intent Received shape on a modern version, and pins the `<Project>`/`<Share>` variants |
| S4 | [`GlitchYou/termux-launch`](https://github.com/GlitchYou/termux-launch) → `Termux_Launch.prj.xml`, and [`dudeofawesome/tasker-tasks`](https://github.com/dudeofawesome/tasker-tasks) → `Play_Music_While_Driving.prf.xml` | the `<Action>` argument layout for `Variable Set` (code 547) and other actions |

S1 is explicitly unofficial ("its info has not been *blessed* by Tasker dev(s)"). It is still the
only published code table, and its codes agree with S2/S3/S4 wherever the two overlap (599, 547,
877, 410, 129), which is why it is used here.

## The four file types (S1)

Tasker decides what a file is by **what nodes it contains**, not by its name:

* one `<Task>` only → a Task file (`.tsk.xml`)
* one `<Scene>` + anonymous `<Task>`s → a Scene file (`.scn.xml`)
* **one `<Profile>` + its `<Task>`s → a Profile file (`.prf.xml`)**
* one `<Project>` whose `<name>` is not `Base` → a Project file (`.prj.xml`)
* anything else → a **Data Backup** (`.xml`, imported through `Data ▸ Restore`)

The suffix matters: if it is wrong, Tasker's import menu does not list the file at all.

This app emits a **Profile file**, so it must contain **exactly one `<Profile>` node** plus the
tasks that profile links to. A file with two `<Profile>` nodes is a Data Backup, and
"Import Profile" will never offer it.

## Skeleton

```xml
<?xml version="1.0" encoding="UTF-8"?>
<TaskerData sr="" dvi="1" tv="6.5.11">
	<Profile sr="prof1" ve="2">
		<cdate>1724658000000</cdate>
		<edate>1724658000000</edate>
		<id>1</id>
		<mid0>1</mid0>
		<nme>Jokarz Timeclock Events</nme>
		<Event sr="con0" ve="2">
			<code>599</code>
			<Str sr="arg0" ve="3">com.randallengineering.jokarztimeclock.EVENT</Str>
			<Int sr="arg1" val="0"/>
			<Int sr="arg2" val="0"/>
			<Str sr="arg3" ve="3"/>
			<Str sr="arg4" ve="3"/>
		</Event>
	</Profile>
	<Task sr="task1">
		<cdate>1724658000000</cdate>
		<edate>1724658000000</edate>
		<id>1</id>
		<nme>Jokarz Timeclock Event</nme>
		<pri>6</pri>
		<Action sr="act0" ve="7">
			<code>547</code>
			<Str sr="arg0" ve="3">%JokarzLastEvent</Str>
			<Str sr="arg1" ve="3">%jokarzevent</Str>
			<Int sr="arg2" val="0"/>
			<Int sr="arg3" val="0"/>
		</Action>
		<Action sr="act1" ve="7">
			<code>547</code>
			<Str sr="arg0" ve="3">%JokarzLastEventDetail</Str>
			<Str sr="arg1" ve="3">%jokarzeventdetail</Str>
			<Int sr="arg2" val="0"/>
			<Int sr="arg3" val="0"/>
		</Action>
	</Task>
</TaskerData>
```

### Root: `<TaskerData>`

`sr=""` (always empty), `dvi="1"`, `tv="<version the file was written for>"`. `tv` is only used for
version-compatibility messaging; `6.5.11` is taken verbatim from S3, a real recent export, so the
file never claims to be newer than the Tasker that has to read it. `<dmetric>` is optional and only
needed when the file contains a `<Scene>` (S1), so this app omits it.

### `<Profile>`

| tag | required | notes |
|-----|----------|-------|
| `<id>` | **yes** (S1) | unique id |
| `<mid0>` | **yes in practice** | the id of the profile's entry task. S1: "at least one of `mid0`/`mid1` needs to be defined". A profile with neither has no task to run and nothing to link to. |
| `<nme>` | optional, always written | the profile name shown in Tasker. Importing a second profile with the same name fails (S1) — rename or delete the first. |
| `<cdate>` / `<edate>` | optional | creation / last-edit ms. Present in S2, absent in S3 — both import. |
| a context child | **yes** (S1) | exactly one of `App`, `Day`, `Event`, `Loc`, `State`, `Time` at minimum |

### `<Event sr="con0" ve="2">` — the trigger

`con0`, `con1`, … number the profile's contexts in order.

The **event type is carried by `<code>`**, a number from Tasker's *Profile Events* table (S1). If
that number is not in the table, Tasker cannot decide what kind of event this is and reports
**"Missing event type"**. This was the whole bug: the pre-2.8.1 export wrote `331`, which in S1's
tables is the **Task Action** `Auto-Sync` — there is no event 331. Intent Received is **`599`**.

Argument layout for **Intent Received** (code 599), identical in S2 (Tasker 4.7u3m) and S3
(Tasker 6.5.11), and matching [S1's arg order for a 5-argument event]:

| attr | type | meaning |
|------|------|---------|
| `arg0` | `<Str>` | the **intent action** to match (`Net.dinglisch...` in the broken file held a package name here) |
| `arg1` | `<Int val=>` | priority, `0` = normal |
| `arg2` | `<Int val=>` | stop-event, `0` = no |
| `arg3` | `<Str>` | category filter, usually empty |
| `arg4` | `<Str>` | data/URI filter, usually empty |

Typed value elements: `<Str sr="argN" ve="3">text</Str>` and `<Int sr="argN" val="0"/>`.
The value attribute on `<Int>` is **`val`**; the broken file used `dvi`, which is not a value
attribute at all. `ve` is the element's own format version (2, 3, 7 as seen in S2/S3/S4).

### `<Task sr="taskN">`

| tag | required | notes |
|-----|----------|-------|
| `<id>` | **yes** (S1) | must equal the `<mid0>`/`<mid1>` that points at it |
| `<nme>` | optional, always written | task name |
| `<pri>` | optional | as in S2 (100) and S3 (10) |
| at least one `<Action>` | **yes** (S1) | |

### `<Action sr="actN" ve="7">`

`act0`, `act1`, … in order. The action type is, again, `<code>` — this time from the *Task
Actions* table (S1). `130` is `Perform Task`; **`877` is `Send Intent`**; **`547` is `Variable
Set`**; `410` is `Write File`; `129` is `JavaScriptlet`.

`Variable Set` (547) arguments, pinned from two real exports (S4): `arg0` = variable name (with
the leading `%`), `arg1` = value, `arg2` = `0` (do-maths off), `arg3` = `0`. S2's `Send Intent`
(877) is `arg0` = action, then data/mime-type/package/class/extras in `arg2`–`arg8` and `arg9` =
target (`2` = Broadcast Receiver). Action arguments that are absent simply take their defaults,
which is why S2 gets away with ten arguments and S4 with four.

## What "Missing event type" means, precisely

Tasker resolves the trigger from `<code>` inside `<Event>`/`<State>`. A code that is not in the
event table leaves the context without a type, so the import aborts with that message before the
task is ever looked at. Note the dialog's advice ("update to the latest Tasker", "sign up for the
beta") is generic text Tasker shows for every import failure — it is not a hint about the version.

## What we deliberately do *not* do

This app is **not** a Locale/Tasker plugin. No `com.twofortyfouram.locale.*` action or condition
receiver, no plugin permission, no blurb extras. Those exist only to let Tasker *run* an app's
plugin actions; they do nothing for the "app tells Tasker something happened" direction, which is
plain `Intent Received`.
