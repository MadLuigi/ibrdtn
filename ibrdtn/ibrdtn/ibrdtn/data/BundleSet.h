/*
 * BundleSet.h
 *
 *  Created on: 18.12.2012
 *      Author: morgenro
 */

#ifndef BUNDLESET_H_
#define BUNDLESET_H_

#include "ibrdtn/data/MetaBundle.h"
#include <ibrcommon/data/BloomFilter.h>
#include <set>

namespace dtn
{
	namespace data
	{
		class BundleSet
		{
		public:
			class Listener {
			public:
				virtual ~Listener() = 0;
				virtual void eventBundleExpired(const dtn::data::MetaBundle&) = 0;
			};

			/**
			 * @param bf_size Initial size fo the bloom-filter.
			 */
			BundleSet(Listener *listener = NULL, size_t bf_size = 1024);
			virtual ~BundleSet();

			virtual void add(const dtn::data::MetaBundle &bundle);
			virtual void clear();
			virtual bool has(const dtn::data::BundleID &bundle) const;

			virtual void expire(const size_t timestamp);

			/**
			 * Returns the number of elements in this set
			 */
			virtual size_t size() const;

			/**
			 * Returns the data length of the serialized BundleSet
			 */
			size_t getLength() const;

			const ibrcommon::BloomFilter& getBloomFilter() const;

			std::set<dtn::data::MetaBundle> getNotIn(ibrcommon::BloomFilter &filter) const;

			friend std::ostream &operator<<(std::ostream &stream, const BundleSet &obj);
			friend std::istream &operator>>(std::istream &stream, BundleSet &obj);

		private:
			class ExpiringBundle
			{
			public:
				ExpiringBundle(const MetaBundle &b);
				virtual ~ExpiringBundle();

				bool operator!=(const ExpiringBundle& other) const;
				bool operator==(const ExpiringBundle& other) const;
				bool operator<(const ExpiringBundle& other) const;
				bool operator>(const ExpiringBundle& other) const;

				const dtn::data::MetaBundle &bundle;
			};

			std::set<dtn::data::MetaBundle> _bundles;
			std::set<ExpiringBundle> _expire;

			ibrcommon::BloomFilter _bf;

			Listener *_listener;

			bool _consistent;
		};
	} /* namespace data */
} /* namespace dtn */
#endif /* BUNDLESET_H_ */