import http from 'k6/http';

export const options = {
    scenarios: {
        steady_rate: {
            executor: 'constant-arrival-rate',
            rate: 100,              // 100 iterations started per timeUnit
            timeUnit: '1s',         // → 100 requests/sec target rate
            duration: '10m',
            preAllocatedVUs: 50,    // VUs reserved upfront to sustain the rate
            maxVUs: 300,            // ceiling if responses slow down and more VUs are needed
        },
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
    http.get('http://localhost:8080/payments/settlement?merchantId=MR-4471');
}