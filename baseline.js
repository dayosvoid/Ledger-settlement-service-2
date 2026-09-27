import http from 'k6/http';

export const options = {
  vus: 200,
  duration: '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  http.get('http://localhost:8080/payments/settlement?merchantId=MR-4471');
}
