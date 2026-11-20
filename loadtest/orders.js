import http from 'k6/http';
import { check } from 'k6';

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

// Place an order through the gateway, then read it back.
// POST returns once stock is reserved (PAYMENT_PENDING). It does not wait
// for payment or for the GraphQL projection.
export const options = {
  vus: 10,
  duration: '30s',
};

export default function () {
  const id = uuidv4();
  const key = `k6${id.replace(/-/g, '')}`;
  const body = JSON.stringify({
    customerId: `cus_${id.replace(/-/g, '').slice(0, 16)}`,
    currency: 'USD',
    paymentMethod: 'CARD',
    paymentToken: 'tok_visa_4242',
    lines: [{ sku: 'SKU-100', quantity: 1, unitPriceMinor: 1500 }],
  });

  const post = http.post('http://localhost:8080/api/orders', body, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': key,
      'X-Correlation-Id': id,
    },
    tags: { name: 'POST /api/orders' },
  });
  const accepted = check(post, {
    'post accepted': (r) => r.status === 202,
  });
  if (!accepted) {
    return;
  }
  const orderId = post.json('orderId');
  const get = http.get(`http://localhost:8080/api/orders/${orderId}`, {
    tags: { name: 'GET /api/orders/{id}' },
  });
  check(get, {
    'get ok': (r) => r.status === 200,
  });
}
