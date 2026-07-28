# Housing Rental Platform Frontend

Vue 3 + Vite 前端，包含房源、用户聊天、通知和 AI 客服页面。

## 启动

双击 `start-frontend.cmd`，或在前端目录执行：

```powershell
npm install
npm run dev
```

默认访问 `http://localhost:5173`，开发代理会把 `/api` 和 `/ws` 转发到 `http://localhost:8080`。

## 构建

```powershell
npm run build
```

AI 客服通过 `fetch` 解析 POST SSE，支持生成状态、工具调用轨迹、停止生成以及收藏和代发房源咨询的二次确认。