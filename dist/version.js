"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.version = void 0;
exports.leerVersion = leerVersion;
const node_fs_1 = require("node:fs");
const node_path_1 = require("node:path");
function leerVersion() {
    try {
        const ruta = (0, node_path_1.join)(__dirname, '..', 'package.json');
        const pkg = JSON.parse((0, node_fs_1.readFileSync)(ruta, 'utf8'));
        return typeof pkg.version === 'string' ? pkg.version : '0.0.0';
    }
    catch {
        return '0.0.0';
    }
}
exports.version = leerVersion();
//# sourceMappingURL=version.js.map