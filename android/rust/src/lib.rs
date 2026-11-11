//! Android bridge for the WordCraft engine.
//!
//! Kotlin never touches the document model. It holds one [`MobileSession`] and talks to it in three
//! ways, mirroring how the egui front end, the CLI and the MCP server already drive the engine:
//!
//! * [`MobileSession::execute`]: run any engine command by id with JSON params (`format.bold`,
//!   `text.insert`, `insert.table`, …). This is the whole editing surface, so every toolbar button
//!   on Android is one call.
//! * Geometry in, geometry out: [`MobileSession::tap`] / [`MobileSession::select_between`] take page
//!   points; [`MobileSession::caret`] / [`MobileSession::selection_rects`] give page points back.
//! * [`MobileSession::render_page`]: the engine's own paginated layout, rasterised to RGBA.
//!
//! All page coordinates are PDF-style points (1/72 inch), origin at the page's top-left.
#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::unimplemented, clippy::todo, clippy::unreachable)]

use std::sync::{Arc, Mutex, MutexGuard};

use serde_json::{Value, json};
use wordcraft_engine::render::{RenderOptions, render_page};
use wordcraft_engine::{Region, Selection, Session, blank_document, io, sample};

uniffi::setup_scaffolding!();

/// Largest raster scale (pixels per point) a caller may ask for; keeps one page under ~100 MB.
const MAX_SCALE: f32 = 6.0;
const MIN_SCALE: f32 = 0.1;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum BridgeError {
    #[error("{reason}")]
    Failed { reason: String },
    #[error("this document needs a password (or the password was wrong)")]
    PasswordRequired,
}

fn fail(message: impl ToString) -> BridgeError {
    BridgeError::Failed { reason: message.to_string() }
}

/// One laid-out page, in points.
#[derive(Clone, Debug, uniffi::Record)]
pub struct PageInfo {
    /// Page number as displayed (after section restarts).
    pub number: u32,
    pub width_pt: f32,
    pub height_pt: f32,
}

/// A rendered page: premultiplied RGBA8, row-major. Copy straight into an `ARGB_8888` `Bitmap`
/// (Android's memory order for that config is R, G, B, A and it is premultiplied by default).
#[derive(Clone, Debug, uniffi::Record)]
pub struct PageImage {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
}

#[derive(Clone, Copy, Debug, uniffi::Record)]
pub struct CaretRect {
    pub page: u32,
    pub x: f32,
    pub y: f32,
    pub height: f32,
}

#[derive(Clone, Copy, Debug, uniffi::Record)]
pub struct SelRect {
    pub page: u32,
    pub x: f32,
    pub y: f32,
    pub w: f32,
    pub h: f32,
}

/// What the toolbars need to light up their buttons. Cheap to compute; call it after every command.
#[derive(Clone, Debug, uniffi::Record)]
pub struct EditorState {
    pub bold: bool,
    pub italic: bool,
    pub underline: bool,
    pub strike: bool,
    pub subscript: bool,
    pub superscript: bool,
    /// `None` when the selection mixes values.
    pub font: Option<String>,
    pub size: Option<f32>,
    /// `RRGGBB` or `auto`.
    pub color: Option<String>,
    pub highlight: Option<String>,
    pub style_name: String,
    /// `left`, `center`, `right`, `justify` or `distribute`.
    pub alignment: String,
    pub can_undo: bool,
    pub can_redo: bool,
    pub dirty: bool,
    pub has_selection: bool,
    pub in_table: bool,
    pub picture_selected: bool,
    pub word_count: u64,
    pub page_count: u32,
    /// Bumps on every document change: use it as the cache key for rendered pages.
    pub revision: u64,
    pub file_name: String,
}

struct Inner {
    session: Session,
    opts: RenderOptions,
}

/// One open document and its editing state. Thread-safe: calls are serialised by a mutex, so Kotlin
/// may render pages from a background dispatcher while the UI thread sends commands.
#[derive(uniffi::Object)]
pub struct MobileSession {
    inner: Mutex<Inner>,
}

impl MobileSession {
    fn wrap(mut session: Session, name: Option<String>) -> Arc<Self> {
        if let Some(n) = name {
            session.file_name = Some(n);
        }
        let opts = RenderOptions { text_darkening: false, ..Default::default() };
        Arc::new(Self { inner: Mutex::new(Inner { session, opts }) })
    }

    /// A poisoned lock only means an earlier call panicked; the engine restores the document in
    /// that case (`Session::run`), so keep going rather than bricking the editor.
    fn lock(&self) -> MutexGuard<'_, Inner> {
        self.inner.lock().unwrap_or_else(|e| e.into_inner())
    }
}

#[uniffi::export]
impl MobileSession {
    /// A new blank document (the region's default paper, A4 or Letter).
    #[uniffi::constructor]
    pub fn new_blank() -> Arc<Self> {
        Self::wrap(Session::new(blank_document(&Region::default())), None)
    }

    /// A new document from one of the built-in templates: `sample`, `letter`, `resume`, `report`.
    #[uniffi::constructor]
    pub fn from_template(kind: String) -> Arc<Self> {
        let doc = match kind.as_str() {
            "letter" => sample::letter(),
            "resume" => sample::resume(),
            "report" => sample::report(),
            "sample" => sample::sample_document(),
            _ => blank_document(&Region::default()),
        };
        Self::wrap(Session::new(doc), None)
    }

    /// Open a file's bytes. `name` decides the format by extension (`.docx`, `.odt`, `.rtf`, `.txt`,
    /// `.md`, `.html`, `.doc`…).
    #[uniffi::constructor]
    pub fn open(name: String, bytes: Vec<u8>, password: Option<String>) -> Result<Arc<Self>, BridgeError> {
        let (doc, _encrypted) = io::open_bytes_in(&name, &bytes, password.as_deref(), &Region::default()).map_err(|e| {
            if io::needs_password(&e) || io::wrong_password(&e) { BridgeError::PasswordRequired } else { fail(e) }
        })?;
        Ok(Self::wrap(Session::new(doc), Some(name)))
    }

    // ---- commands -----------------------------------------------------------------------------

    /// Run an engine command. `params_json` is a JSON object (`""` = `{}`). Returns the command's
    /// JSON result. Fails with the engine's own message (disabled command, bad params, …).
    pub fn execute(&self, command: String, params_json: String) -> Result<String, BridgeError> {
        let params: Value = if params_json.trim().is_empty() { json!({}) } else { serde_json::from_str(&params_json).map_err(fail)? };
        let mut g = self.lock();
        g.session.run(&command, &params).map(|v| v.to_string()).map_err(fail)
    }

    /// Every command the engine registers, as JSON (`id`, `label`, `location`, `shortcut`, `params`).
    pub fn commands_json(&self) -> String {
        self.lock().session.registry.describe().to_string()
    }

    /// Why a command is unavailable right now, or `None` when it can run.
    pub fn why_disabled(&self, command: String) -> Option<String> {
        self.lock().session.why_disabled(&command).map(str::to_owned)
    }

    /// Of `ids`, the commands that exist and are enabled at the caret right now (ribbon enabled state).
    pub fn enabled_among(&self, ids: Vec<String>) -> Vec<String> {
        let g = self.lock();
        let s = &g.session;
        ids.into_iter().filter(|id| s.registry.get(id).is_some() && s.why_disabled(id).is_none()).collect()
    }

    /// Paste text from the Android clipboard. When it is the text WordCraft itself last copied, the
    /// formatted fragment is pasted instead, so Copy/Paste inside the app keeps formatting.
    pub fn paste(&self, system_text: String) -> Result<(), BridgeError> {
        let mut g = self.lock();
        let s = &mut g.session;
        let params = if s.clipboard.is_some() && s.clipboard_text == system_text { json!({}) } else { json!({ "text": system_text }) };
        s.run("edit.paste", &params).map(|_| ()).map_err(fail)
    }

    /// Plain text of the engine clipboard: after `edit.copy` / `edit.cut`, hand this to Android's
    /// `ClipboardManager`.
    pub fn clipboard_text(&self) -> String {
        self.lock().session.clipboard_text.clone()
    }

    pub fn selected_text(&self) -> String {
        self.lock().session.selected_text()
    }

    /// Insert a picture (PNG, JPEG, GIF, WebP, BMP, TIFF bytes) at the caret. `width_pt` scales it
    /// to that width (aspect kept); `None` uses the engine's default.
    pub fn insert_picture(&self, bytes: Vec<u8>, width_pt: Option<f32>, alt: Option<String>) -> Result<(), BridgeError> {
        let mut params = json!({ "data": base64(&bytes) });
        if let Some(w) = width_pt.filter(|w| w.is_finite() && *w > 0.0) {
            params["width"] = json!(w);
        }
        if let Some(a) = alt {
            params["alt"] = json!(a);
        }
        self.lock().session.run("insert.picture", &params).map(|_| ()).map_err(fail)
    }

    pub fn undo(&self) -> bool {
        self.lock().session.undo()
    }

    pub fn redo(&self) -> bool {
        self.lock().session.redo()
    }

    // ---- pages --------------------------------------------------------------------------------

    pub fn pages(&self) -> Vec<PageInfo> {
        let mut g = self.lock();
        let layout = g.session.layout();
        layout.pages.iter().map(|p| PageInfo { number: p.number, width_pt: p.w, height_pt: p.h }).collect()
    }

    pub fn page_count(&self) -> u32 {
        let mut g = self.lock();
        u32::try_from(g.session.layout().pages.len()).unwrap_or(u32::MAX)
    }

    /// Rasterise one page at `scale` pixels per point (1.0 = 72 dpi). Clamped to a safe range.
    pub fn render_page(&self, index: u32, scale: f32) -> Result<PageImage, BridgeError> {
        let scale = if scale.is_finite() { scale.clamp(MIN_SCALE, MAX_SCALE) } else { 1.0 };
        let mut g = self.lock();
        let inner = &mut *g;
        let layout = inner.session.layout();
        let page = layout.pages.get(index as usize).ok_or_else(|| fail(format!("no page {index}")))?;
        let r = render_page(&inner.session.doc, page, scale, &inner.opts);
        Ok(PageImage { width: r.width, height: r.height, rgba: r.pixels })
    }

    // ---- caret and selection ------------------------------------------------------------------

    pub fn caret(&self) -> Option<CaretRect> {
        let mut g = self.lock();
        let s = &mut g.session;
        let layout = s.layout();
        let c = layout.caret_on(&s.sel.focus, s.page_hint)?;
        Some(CaretRect { page: u32::try_from(c.page).ok()?, x: c.x, y: c.top, height: c.height })
    }

    /// Highlight rectangles of the current selection (empty for a bare caret).
    pub fn selection_rects(&self) -> Vec<SelRect> {
        let mut g = self.lock();
        let s = &mut g.session;
        let (a, b) = s.sel.ordered();
        if a == b {
            return Vec::new();
        }
        let layout = s.layout();
        layout
            .selection_rects(&s.doc, &a, &b, s.page_hint)
            .into_iter()
            .filter_map(|(page, r)| Some(SelRect { page: u32::try_from(page).ok()?, x: r.x, y: r.y, w: r.w, h: r.h }))
            .collect()
    }

    /// `page` is 0-based, like the engine's hit-test and like `select_between`.
    /// Put the caret where a tap at (`x`, `y`) points on `page`; with `extend` the selection grows
    /// from where it was instead.
    pub fn tap(&self, page: u32, x: f32, y: f32, extend: bool) -> Result<(), BridgeError> {
        let params = json!({ "page": page, "x": x, "y": y, "extend": extend });
        self.lock().session.run("caret.set", &params).map(|_| ()).map_err(fail)
    }

    /// Tap, then select the word under it (double tap).
    pub fn select_word_at(&self, page: u32, x: f32, y: f32) -> Result<(), BridgeError> {
        let mut g = self.lock();
        let s = &mut g.session;
        s.run("caret.set", &json!({ "page": page, "x": x, "y": y })).map_err(fail)?;
        s.run("select.word", &json!({})).map(|_| ()).map_err(fail)
    }

    /// Tap, then select the paragraph under it (triple tap).
    pub fn select_paragraph_at(&self, page: u32, x: f32, y: f32) -> Result<(), BridgeError> {
        let mut g = self.lock();
        let s = &mut g.session;
        s.run("caret.set", &json!({ "page": page, "x": x, "y": y })).map_err(fail)?;
        s.run("select.paragraph", &json!({})).map(|_| ()).map_err(fail)
    }

    /// Select from one page point to another (selection handles and long-press drag). The two
    /// points may be in either order; either handle can move.
    pub fn select_between(&self, a_page: u32, a_x: f32, a_y: f32, b_page: u32, b_x: f32, b_y: f32) -> Result<(), BridgeError> {
        let mut g = self.lock();
        let s = &mut g.session;
        let story = s.sel.focus.story;
        let layout = s.layout();
        let a = layout.hit(a_page as usize, a_x, a_y, story).ok_or_else(|| fail("nothing at the start point"))?;
        let b = layout.hit(b_page as usize, b_x, b_y, story).ok_or_else(|| fail("nothing at the end point"))?;
        s.sel = Selection { anchor: a, focus: b };
        s.goal_x = None;
        s.page_hint = b_page as usize;
        s.close_typing();
        Ok(())
    }

    // ---- state --------------------------------------------------------------------------------

    pub fn state(&self) -> EditorState {
        let mut g = self.lock();
        let s = &mut g.session;
        let fs = s.run("format.state", &json!({})).unwrap_or(Value::Null);
        let flag = |k: &str| fs.get(k).and_then(Value::as_bool).unwrap_or(false);
        let text = |k: &str| fs.get(k).and_then(Value::as_str).map(str::to_owned);
        let alignment = s
            .doc
            .para_at(&s.sel.focus)
            .and_then(|p| p.props.align)
            .and_then(|a| serde_json::to_value(a).ok())
            .and_then(|v| v.as_str().map(str::to_owned))
            .unwrap_or_else(|| "left".to_owned());
        let enabled = |s: &Session, id: &str| s.registry.get(id).is_some() && s.why_disabled(id).is_none();
        let page_count = u32::try_from(s.layout().pages.len()).unwrap_or(u32::MAX);
        EditorState {
            bold: flag("bold"),
            italic: flag("italic"),
            underline: flag("underline"),
            strike: flag("strike"),
            subscript: flag("subscript"),
            superscript: flag("superscript"),
            font: text("font"),
            size: fs.get("size").and_then(Value::as_f64).map(|v| v as f32),
            color: text("color"),
            highlight: text("highlight"),
            style_name: text("styleName").unwrap_or_else(|| "Normal".into()),
            alignment,
            can_undo: s.can_undo(),
            can_redo: s.can_redo(),
            dirty: s.dirty,
            has_selection: !s.sel.is_collapsed(),
            in_table: enabled(s, "table.deleteRow"),
            picture_selected: enabled(s, "picture.size"),
            word_count: s.word_count() as u64,
            page_count,
            revision: s.rev(),
            file_name: s.file_name.clone().unwrap_or_else(|| "Untitled".into()),
        }
    }

    // ---- saving -------------------------------------------------------------------------------

    /// Serialise for saving in place: the format follows `name`'s extension, the document is stamped
    /// as saved and marked clean.
    pub fn save(&self, name: String) -> Result<Vec<u8>, BridgeError> {
        let mut g = self.lock();
        let s = &mut g.session;
        s.stamp_save();
        let bytes = io::save_bytes(&name, &s.doc).map_err(fail)?;
        s.dirty = false;
        s.file_name = Some(name);
        Ok(bytes)
    }

    /// Serialise a copy (PDF, ODT, TXT, HTML, …) without touching the document's saved state.
    pub fn export(&self, name: String) -> Result<Vec<u8>, BridgeError> {
        let g = self.lock();
        io::save_bytes(&name, &g.session.doc).map_err(fail)
    }
}

/// Register fonts from directories such as `/system/fonts` and the app's own font folder.
///
/// The engine's font scan has no Android search path (it falls through to `/usr/share/fonts`), so
/// without this call nothing has a face to draw with. Call once at startup, before the first layout.
/// Returns the number of faces added. Files over 24 MB (CJK collections) are skipped and the total
/// is capped, to keep memory sane on phones; install those through the engine's font store instead.
#[uniffi::export]
pub fn register_fonts(dirs: Vec<String>) -> u32 {
    const MAX_FILE: u64 = 24 * 1024 * 1024;
    const MAX_TOTAL: u64 = 160 * 1024 * 1024;
    let mut files: Vec<(Vec<u8>, String)> = Vec::new();
    let mut total = 0u64;
    'dirs: for dir in dirs {
        let Ok(rd) = std::fs::read_dir(&dir) else { continue };
        let mut paths: Vec<std::path::PathBuf> = rd.flatten().map(|e| e.path()).collect();
        paths.sort();
        for p in paths {
            let ext = p.extension().and_then(|e| e.to_str()).map(str::to_ascii_lowercase).unwrap_or_default();
            if !matches!(ext.as_str(), "ttf" | "otf" | "ttc" | "otc" | "woff" | "woff2") {
                continue;
            }
            let Ok(meta) = std::fs::metadata(&p) else { continue };
            if meta.len() > MAX_FILE {
                continue;
            }
            if total.saturating_add(meta.len()) > MAX_TOTAL {
                break 'dirs;
            }
            let Ok(bytes) = std::fs::read(&p) else { continue };
            let Ok(data) = wordcraft_fonts::decode_font_file(&bytes) else { continue };
            total += meta.len();
            let stem = p.file_stem().and_then(|s| s.to_str()).unwrap_or("font").to_owned();
            files.push((data, stem));
        }
    }
    u32::try_from(wordcraft_fonts::FontDb::global().add_document_fonts(files)).unwrap_or(u32::MAX)
}

/// Standard base64 (RFC 4648, padded) — the engine takes picture bytes as base64 text.
fn base64(data: &[u8]) -> String {
    const T: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(data.len().div_ceil(3) * 4);
    for chunk in data.chunks(3) {
        let b = [chunk.first().copied().unwrap_or(0), chunk.get(1).copied().unwrap_or(0), chunk.get(2).copied().unwrap_or(0)];
        let n = (u32::from(b[0]) << 16) | (u32::from(b[1]) << 8) | u32::from(b[2]);
        let ch = |shift: u32| char::from(T.get(((n >> shift) & 63) as usize).copied().unwrap_or(b'='));
        out.push(ch(18));
        out.push(ch(12));
        out.push(if chunk.len() > 1 { ch(6) } else { '=' });
        out.push(if chunk.len() > 2 { ch(0) } else { '=' });
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn base64_matches_rfc4648_vectors() {
        assert_eq!(base64(b""), "");
        assert_eq!(base64(b"f"), "Zg==");
        assert_eq!(base64(b"fo"), "Zm8=");
        assert_eq!(base64(b"foo"), "Zm9v");
        assert_eq!(base64(b"foobar"), "Zm9vYmFy");
    }

    #[test]
    fn type_format_undo_and_render_round_trip() {
        let s = MobileSession::from_template("sample".into());
        assert!(s.page_count() >= 1);
        s.tap(0, 120.0, 140.0, false).unwrap();
        s.execute("text.insert".into(), r#"{"text":"Hello"}"#.into()).unwrap();
        assert!(s.state().dirty);
        s.execute("select.word".into(), String::new()).unwrap();
        s.execute("format.bold".into(), String::new()).unwrap();
        assert!(s.state().bold);
        assert!(s.undo());
        let img = s.render_page(0, 1.0).unwrap();
        assert_eq!(img.rgba.len() as u32, img.width * img.height * 4);
        let docx = s.save("t.docx".into()).unwrap();
        assert!(MobileSession::open("t.docx".into(), docx, None).is_ok());
        assert!(s.export("t.pdf".into()).unwrap().starts_with(b"%PDF"));
    }

    #[test]
    fn unknown_command_is_an_error_not_a_panic() {
        let s = MobileSession::new_blank();
        assert!(s.execute("no.such.command".into(), String::new()).is_err());
        assert!(s.execute("text.insert".into(), "not json".into()).is_err());
    }
}
