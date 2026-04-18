import { jsx as _jsx } from "react/jsx-runtime";
import React from "react";
import { createRoot } from "react-dom/client";
import { OsanwallCanvasApp } from "./screen";
import "./styles.css";
const root = document.getElementById("root");
if (!root)
    throw new Error("Missing root element");
createRoot(root).render(_jsx(React.StrictMode, { children: _jsx(OsanwallCanvasApp, {}) }));
