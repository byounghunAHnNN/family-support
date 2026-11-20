/**
 * 서버 호출 공용. 응답은 ApiResponse 껍데기 { success, data, error } — 성공이면 data를, 실패면 서버의 한국어 메시지로 Error를 던진다.
 */
const Api = (() => {
    async function request(method, url, body) {
        let response;
        try {
            response = await fetch(url, {
                method,
                headers: body ? {'Content-Type': 'application/json'} : {},
                body: body ? JSON.stringify(body) : undefined,
            });
        } catch (e) {
            throw new Error('서버에 연결하지 못했습니다.');
        }
        let json = null;
        try {
            json = await response.json();
        } catch (e) {
            // 본문이 JSON이 아님 — 아래에서 상태 코드로 처리
        }
        if (!response.ok || !json || !json.success) {
            throw new Error(json && json.error ? json.error.message : '요청을 처리하지 못했습니다. (' + response.status + ')');
        }
        return json.data;
    }

    return {
        get: (url) => request('GET', url),
        post: (url, body) => request('POST', url, body),
    };
})();
