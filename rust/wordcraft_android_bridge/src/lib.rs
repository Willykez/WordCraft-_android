//! Android JNI adapter for the unmodified storytold/wordcraft 0.16.0 engine.
//!
//! The document model, commands, undo history, file formats and layout stay in
//! the upstream Rust engine. This crate only translates Android/JNI calls.

use std::sync::{Mutex, OnceLock};

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jfloat, jint, jstring};
use jni::JNIEnv;
use serde_json::{Value, json};
use wordcraft_engine::{Session, doc::Document};

static SESSION: OnceLock<Mutex<Session>> = OnceLock::new();

fn session() -> &'static Mutex<Session> {
    SESSION.get_or_init(|| Mutex::new(Session::new(Document::new())))
}

fn java_string(env: &mut JNIEnv<'_>, value: impl AsRef<str>) -> jstring {
    env.new_string(value.as_ref())
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<String, String> {
    env.get_string(value)
        .map(|value| value.to_string_lossy().into_owned())
        .map_err(|error| format!("Could not read Java string: {error}"))
}

fn json_result(result: Result<Value, impl std::fmt::Display>) -> String {
    match result {
        Ok(value) => json!({"ok": true, "result": value}).to_string(),
        Err(error) => json!({"ok": false, "error": error.to_string()}).to_string(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_engineStatus(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let status = match session().lock() {
        Ok(_) => "WordCraft Rust engine 0.16.0 is loaded",
        Err(_) => "WordCraft engine session lock failed",
    };
    java_string(&mut env, status)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeGetText(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let text = match session().lock() {
        Ok(current) => current.doc.plain_text(wordcraft_engine::doc::StoryRef::Body),
        Err(_) => String::new(),
    };
    java_string(&mut env, text)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeGetSelection(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let text = match session().lock() {
        Ok(current) => current.selected_text(),
        Err(_) => String::new(),
    };
    java_string(&mut env, text)
}

/// Dispatch any registered WordCraft command by stable command id and JSON params.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeRunCommand(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    command: JString<'_>,
    params_json: JString<'_>,
) -> jstring {
    let command = match read_string(&mut env, &command) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, json!({"ok": false, "error": error}).to_string()),
    };
    let params_text = match read_string(&mut env, &params_json) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, json!({"ok": false, "error": error}).to_string()),
    };
    let params: Value = match serde_json::from_str(&params_text) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, json!({"ok": false, "error": format!("Invalid command parameters: {error}")}).to_string()),
    };
    let result = match session().lock() {
        Ok(mut current) => json_result(current.run(&command, &params)),
        Err(_) => json!({"ok": false, "error": "WordCraft engine session lock failed"}).to_string(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeSetText(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    value: JString<'_>,
) -> jstring {
    let text = match read_string(&mut env, &value) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, error),
    };
    let result = match session().lock() {
        Ok(mut current) => current.run("document.setText", &json!({"text": text}))
            .map(|_| "Document text replaced by WordCraft".to_owned())
            .unwrap_or_else(|error| format!("WordCraft edit failed: {error}")),
        Err(_) => "WordCraft engine session lock failed".to_owned(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeFormatAll(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    command: JString<'_>,
) -> jstring {
    let command = match read_string(&mut env, &command) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, error),
    };
    let result = match session().lock() {
        Ok(mut current) => current.run("select.all", &json!({}))
            .and_then(|_| current.run(&command, &json!({})))
            .map(|_| format!("Applied {command} to the whole document"))
            .unwrap_or_else(|error| format!("{command} failed: {error}")),
        Err(_) => "WordCraft engine session lock failed".to_owned(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeUndo(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let result = match session().lock() {
        Ok(mut current) => current.run("edit.undo", &json!({}))
            .map(|_| "Undo completed".to_owned())
            .unwrap_or_else(|error| format!("Undo failed: {error}")),
        Err(_) => "WordCraft engine session lock failed".to_owned(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeRedo(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let result = match session().lock() {
        Ok(mut current) => current.run("edit.redo", &json!({}))
            .map(|_| "Redo completed".to_owned())
            .unwrap_or_else(|error| format!("Redo failed: {error}")),
        Err(_) => "WordCraft engine session lock failed".to_owned(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativePageCount(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jint {
    match session().lock() {
        Ok(mut current) => current.layout().pages.len().min(jint::MAX as usize) as jint,
        Err(_) => 0,
    }
}

/// Open any format supported by WordCraft's extension dispatcher, based on its filename.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeOpenDocument(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    name: JString<'_>,
    bytes: JByteArray<'_>,
) -> jstring {
    let name = match read_string(&mut env, &name) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, error),
    };
    let bytes = match env.convert_byte_array(bytes) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, format!("Could not read document bytes: {error}")),
    };
    let result = match wordcraft_engine::io::open_bytes(&name, &bytes) {
        Ok(document) => match session().lock() {
            Ok(mut current) => {
                current.set_document(document);
                format!("Opened {name} with WordCraft 0.16.0")
            }
            Err(_) => "WordCraft engine session lock failed".to_owned(),
        },
        Err(error) => format!("Could not open {name}: {error}"),
    };
    java_string(&mut env, result)
}

/// Export by extension: docx, pdf, rtf, odt and other engine-supported formats.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeSaveDocument(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    name: JString<'_>,
) -> jbyteArray {
    let name = match read_string(&mut env, &name) {
        Ok(value) => value,
        Err(_) => return std::ptr::null_mut(),
    };
    let bytes = match session().lock() {
        Ok(current) => match wordcraft_engine::io::save_bytes(&name, &current.doc) {
            Ok(bytes) => bytes,
            Err(_) => return std::ptr::null_mut(),
        },
        Err(_) => return std::ptr::null_mut(),
    };
    env.byte_array_from_slice(&bytes)
        .map(|array| array.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeGetLayoutSummary(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let summary = match session().lock() {
        Ok(mut current) => current.run("document.layout", &json!({}))
            .map(|value| value.to_string())
            .unwrap_or_else(|error| json!({"error": error.to_string()}).to_string()),
        Err(_) => json!({"error": "WordCraft engine session lock failed"}).to_string(),
    };
    java_string(&mut env, summary)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeCommandCatalog(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    java_string(&mut env, wordcraft_engine::catalog::CATALOG)
}

// ---------------------------------------------------------------------------
// Page rendering, hit testing and caret geometry (all from the upstream engine)
// ---------------------------------------------------------------------------

/// Page and body-area sizes in points: `{"pages":[{"w","h","bx","by","bw","bh"}, ...]}`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeLayoutInfo(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let text = match session().lock() {
        Ok(mut current) => {
            let layout = current.layout();
            let pages: Vec<Value> = layout
                .pages
                .iter()
                .map(|p| json!({"w": p.w, "h": p.h, "bx": p.body.x, "by": p.body.y, "bw": p.body.w, "bh": p.body.h}))
                .collect();
            json!({"ok": true, "pages": pages}).to_string()
        }
        Err(_) => json!({"ok": false, "error": "WordCraft engine session lock failed"}).to_string(),
    };
    java_string(&mut env, text)
}

/// Render one page with the engine's own rasteriser.
/// Returns `[width: u32 LE][height: u32 LE][RGBA8 premultiplied pixels...]`, or null on failure.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeRenderPage(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    page: jint,
    scale: jfloat,
) -> jbyteArray {
    let Ok(mut current) = session().lock() else { return std::ptr::null_mut() };
    let layout = current.layout();
    let Some(page_layout) = usize::try_from(page).ok().and_then(|i| layout.pages.get(i)) else {
        return std::ptr::null_mut();
    };
    let opts = wordcraft_engine::render::RenderOptions::default();
    let rendered = wordcraft_engine::render::render_page(&current.doc, page_layout, scale, &opts);
    let mut out = Vec::with_capacity(8 + rendered.pixels.len());
    out.extend_from_slice(&rendered.width.to_le_bytes());
    out.extend_from_slice(&rendered.height.to_le_bytes());
    out.extend_from_slice(&rendered.pixels);
    env.byte_array_from_slice(&out).map(|array| array.into_raw()).unwrap_or(std::ptr::null_mut())
}

/// Move the caret (or extend the selection) to the document position under a page point
/// (page coordinates in points). Returns `{"ok":true,"hit":bool}`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeHitTest(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    page: jint,
    x: jfloat,
    y: jfloat,
    extend: jboolean,
) -> jstring {
    let text = match session().lock() {
        Ok(mut current) => {
            let layout = current.layout();
            let story = current.sel.focus.story;
            let hit = usize::try_from(page).ok().and_then(|i| layout.hit(i, x, y, story));
            match hit {
                Some(pos) => {
                    let pos = current.doc.clamp(&pos);
                    let anchor = if extend != 0 { current.sel.anchor.clone() } else { pos.clone() };
                    let params = json!({"anchor": anchor, "focus": pos});
                    json_result(current.run("select.range", &params).map(|_| json!({"hit": true})))
                }
                None => json!({"ok": true, "result": {"hit": false}}).to_string(),
            }
        }
        Err(_) => json!({"ok": false, "error": "WordCraft engine session lock failed"}).to_string(),
    };
    java_string(&mut env, text)
}

/// Caret and selection geometry in page coordinates (points):
/// `{"collapsed":bool,"focus":{page,x,top,height,width}|null,"anchor":{...}|null}`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeCaretInfo(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let text = match session().lock() {
        Ok(mut current) => {
            let layout = current.layout();
            let geometry = |pos: &wordcraft_engine::doc::Pos| {
                layout.caret(pos).map(|c| json!({
                    "page": c.page, "x": c.x, "top": c.top, "height": c.height, "width": c.width
                }))
            };
            json!({
                "ok": true,
                "collapsed": current.sel.is_collapsed(),
                "focus": geometry(&current.sel.focus),
                "anchor": geometry(&current.sel.anchor),
            })
            .to_string()
        }
        Err(_) => json!({"ok": false, "error": "WordCraft engine session lock failed"}).to_string(),
    };
    java_string(&mut env, text)
}

// ---------------------------------------------------------------------------
// Ribbon model: the engine's own command catalog joined with its command registry
// ---------------------------------------------------------------------------

/// Every ribbon entry of the desktop app (tab, group, label, command id) with the command's
/// parameter description and shortcut, and whether this build implements it:
/// `{"ok":true,"items":[{"tab","group","label","id","exists","params","shortcut"}]}`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeRibbon(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let text = match session().lock() {
        Ok(current) => {
            let items: Vec<Value> = wordcraft_engine::catalog::entries()
                .into_iter()
                .map(|(tab, group, label, id)| {
                    let spec = current.registry.get(id);
                    json!({
                        "tab": tab, "group": group, "label": label, "id": id,
                        "exists": spec.is_some(),
                        "params": spec.map(|s| s.params).unwrap_or("{}"),
                        "shortcut": spec.map(|s| s.shortcut).unwrap_or(""),
                    })
                })
                .collect();
            json!({"ok": true, "items": items}).to_string()
        }
        Err(_) => json!({"ok": false, "error": "WordCraft engine session lock failed"}).to_string(),
    };
    java_string(&mut env, text)
}

/// All registered commands (id, label, location, shortcut, params, mutates) as a JSON array,
/// for the command search palette.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeCommandRegistry(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let text = match session().lock() {
        Ok(current) => current.registry.describe().to_string(),
        Err(_) => "[]".to_string(),
    };
    java_string(&mut env, text)
}

/// Commands that are currently disabled, with the engine's reason: `{"id":"reason", ...}`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeDisabledCommands(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let text = match session().lock() {
        Ok(current) => {
            let session: &Session = &current;
            let mut disabled = serde_json::Map::new();
            for spec in session.registry.all() {
                if let Some(reason) = (spec.enabled)(session) {
                    disabled.insert(spec.id.to_string(), json!(reason));
                }
            }
            Value::Object(disabled).to_string()
        }
        Err(_) => "{}".to_string(),
    };
    java_string(&mut env, text)
}

/// Headings of the body for the navigation pane:
/// `{"ok":true,"headings":[{"level":1,"text":"...","pos":<Pos>}]}`; `pos` can be passed to
/// `select.range` (as both anchor and focus) to jump there.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeHeadings(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    use wordcraft_engine::doc::{Pos, StoryRef};
    let text = match session().lock() {
        Ok(current) => {
            let doc = &current.doc;
            let mut items: Vec<Value> = Vec::new();
            for path in doc.para_paths(StoryRef::Body) {
                let Some(p) = doc.para(StoryRef::Body, &path) else { continue };
                let rp = doc.styles.resolve_para(&p.props);
                if let Some(level) = rp.outline_level {
                    let txt = p.plain_text().trim().to_string();
                    if txt.is_empty() || rp.style.starts_with("TOC") {
                        continue;
                    }
                    let pos = Pos::new(StoryRef::Body, path.clone(), 0);
                    items.push(json!({"level": level, "text": txt, "pos": pos}));
                }
            }
            json!({"ok": true, "headings": items}).to_string()
        }
        Err(_) => json!({"ok": false, "error": "WordCraft engine session lock failed"}).to_string(),
    };
    java_string(&mut env, text)
}

#[cfg(test)]
mod tests {
    //! Exercises the exact engine call sequence the JNI functions use (no JVM needed).
    use super::*;

    #[test]
    fn type_hit_caret_render_roundtrip() {
        let mut s = Session::new(Document::new());
        s.run("text.insert", &json!({"text": "Hello Android"})).unwrap();
        s.run("select.all", &json!({})).unwrap();
        s.run("format.bold", &json!({})).unwrap();

        let layout = s.layout();
        assert!(!layout.pages.is_empty(), "document must lay out at least one page");
        let page = &layout.pages[0];
        assert!(page.w > 100.0 && page.h > 100.0);

        // Hit-test inside the first line of the body and move the caret there.
        let (x, y) = (page.body.x + 20.0, page.body.y + 5.0);
        let pos = layout.hit(0, x, y, s.sel.focus.story).expect("hit inside first line");
        let pos = s.doc.clamp(&pos);
        let params = json!({"anchor": pos, "focus": pos});
        s.run("select.range", &params).unwrap();
        assert!(s.sel.is_collapsed());

        let layout = s.layout();
        let caret = layout.caret(&s.sel.focus).expect("caret geometry");
        assert_eq!(caret.page, 0);
        assert!(caret.height > 0.0);

        // Typing at the caret changes the text; rendering produces ink, not a blank page.
        s.run("text.insert", &json!({"text": "X"})).unwrap();
        assert!(s.doc.plain_text(wordcraft_engine::doc::StoryRef::Body).contains('X'));
        let layout = s.layout();
        let opts = wordcraft_engine::render::RenderOptions::default();
        let img = wordcraft_engine::render::render_page(&s.doc, &layout.pages[0], 1.0, &opts);
        assert_eq!(img.pixels.len(), (img.width * img.height * 4) as usize);
        let dark = img.pixels.chunks(4).filter(|p| p[0] < 128 && p[1] < 128 && p[2] < 128).count();
        assert!(dark > 20, "expected rendered glyph pixels, found {dark}");

        // Undo and a table insert go through the same command registry.
        s.run("edit.undo", &json!({})).unwrap();
        s.run("insert.table", &json!({"rows": 2, "cols": 2})).unwrap();
        s.run("table.insertRowBelow", &json!({})).unwrap();
        // A tiny picture (1x1 PNG) through insert.picture with base64 data.
        let png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==";
        s.run("insert.picture", &json!({"data": png, "width": 120})).unwrap();
    }

    #[test]
    fn ribbon_catalog_joins_registry() {
        let s = Session::new(Document::new());
        let entries = wordcraft_engine::catalog::entries();
        let live = entries.iter().filter(|(_, _, _, id)| s.registry.get(id).is_some()).count();
        assert!(entries.len() > 400, "catalog has {} entries", entries.len());
        assert!(live * 100 / entries.len() >= 90, "only {live}/{} catalog entries are implemented", entries.len());
        let disabled = s.registry.all().iter().filter(|c| (c.enabled)(&s).is_some()).count();
        assert!(disabled < s.registry.all().len());
        eprintln!("ribbon: {} entries, {} implemented, {} disabled in a blank doc", entries.len(), live, disabled);
    }

    #[test]
    fn headings_for_navigation_pane() {
        use wordcraft_engine::doc::StoryRef;
        let mut s = Session::new(Document::new());
        s.run("text.insert", &json!({"text": "Intro"})).unwrap();
        s.run("para.heading1", &json!({})).unwrap();
        s.run("text.newParagraph", &json!({})).unwrap();
        s.run("para.normal", &json!({})).unwrap();
        s.run("text.insert", &json!({"text": "Body text"})).unwrap();
        let doc = &s.doc;
        let mut found = Vec::new();
        for path in doc.para_paths(StoryRef::Body) {
            let p = doc.para(StoryRef::Body, &path).unwrap();
            let rp = doc.styles.resolve_para(&p.props);
            if let Some(level) = rp.outline_level {
                found.push((level, p.plain_text()));
            }
        }
        assert_eq!(found.len(), 1, "exactly the Heading 1 paragraph: {found:?}");
        assert_eq!(found[0].1.trim(), "Intro");
    }
}
