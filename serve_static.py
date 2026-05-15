import http.server
import os
import http.client
import urllib.request

BACKEND = 'http://127.0.0.1:8080'

class MyHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    extensions_map = http.server.SimpleHTTPRequestHandler.extensions_map.copy()
    extensions_map.update({
        '.js': 'application/javascript',
        '.mjs': 'application/javascript',
        '.css': 'text/css',
        '.json': 'application/json',
        '.wasm': 'application/wasm',
    })

    def end_headers(self):
        self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
        super().end_headers()

    def do_GET(self):
        if self.path.startswith('/api/'):
            return self.proxy()
        file_path = self.translate_path(self.path)
        if not os.path.exists(file_path) or os.path.isdir(file_path):
            self.path = '/index.html'
        super().do_GET()

    def do_POST(self):
        if self.path.startswith('/api/'):
            return self.proxy()
        self.send_error(404)

    def do_PUT(self):
        if self.path.startswith('/api/'):
            return self.proxy()
        self.send_error(404)

    def do_DELETE(self):
        if self.path.startswith('/api/'):
            return self.proxy()
        self.send_error(404)

    def proxy(self):
        try:
            body = None
            cl = self.headers.get('Content-Length')
            if cl and int(cl) > 0:
                body = self.rfile.read(int(cl))
            headers = {k: v for k, v in self.headers.items() if k.lower() not in ('host', 'content-length', 'transfer-encoding', 'accept-encoding')}
            req = urllib.request.Request(BACKEND + self.path, data=body, headers=headers, method=self.command)
            resp = urllib.request.urlopen(req, timeout=60)
            self.send_response(resp.status)
            for k, v in resp.headers.items():
                if k.lower() not in ('transfer-encoding', 'content-encoding', 'content-length'):
                    self.send_header(k, v)
            self.end_headers()
            self.wfile.write(resp.read())
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            self.end_headers()
            self.wfile.write(e.read())
        except Exception as e:
            self.send_error(502, f'Proxy error')

if __name__ == '__main__':
    os.chdir(r'C:\Users\fantuan\Desktop\javaweb\rent\frontend\dist')
    server = http.server.HTTPServer(('0.0.0.0', 3000), MyHTTPRequestHandler)
    print('Serving on port 3000 (SPA + API proxy)')
    server.serve_forever()
