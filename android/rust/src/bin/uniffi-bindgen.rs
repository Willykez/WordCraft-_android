//! `cargo run --bin uniffi-bindgen -- generate --library <libwordcraft_android.so> --language kotlin --out-dir <dir>`
fn main() {
    uniffi::uniffi_bindgen_main()
}
